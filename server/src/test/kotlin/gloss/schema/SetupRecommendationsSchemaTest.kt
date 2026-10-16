package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class SetupRecommendationsSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun account(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "r-" + id.toString().take(12))
            .update()
        return id
    }

    private fun session(account: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, created_at, updated_at)
                values (:id, 0, :account, 'searching', now(), now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .update()
        return id
    }

    private fun message(
        session: UUID,
        account: UUID,
        role: String,
        kind: String,
    ) = jdbc
        .sql(
            """
            insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
            select :id, :session, :account, coalesce(max(seq), 0) + 1, :role, :kind, '{}', now()
            from setup_message where session_id = :session
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("session", session)
        .param("account", account)
        .param("role", role)
        .param("kind", kind)
        .update()

    private fun search(
        account: UUID,
        session: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into web_search (id, account_id, session_id, asked_at)
                values (:id, :account, :session, now() - interval '1 second')
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("session", session)
            .update()
        return id
    }

    private fun answer(
        id: UUID,
        outcome: String,
        results: Int?,
        cost: Long?,
    ) = jdbc
        .sql(
            """
            update web_search set answered_at = now(), outcome = :outcome, results = :results, cost_micros = :cost,
                                  request_id = case when :outcome = 'answered' then 'req' end
            where id = :id
            """.trimIndent(),
        ).param("id", id)
        .param("outcome", outcome)
        .param("results", results)
        .param("cost", cost)
        .update()

    @Test
    fun `a model's calls are their own purpose in the ledger`() {
        assertThatCode {
            jdbc
                .sql(
                    """
                    insert into usage_ledger (occurred_at, billing_month, account_id, reservation_id, kind,
                                              amount_micros, purpose, model, price_hit, price_miss, price_out)
                    values (now(), '2026-10-01', :account, :reservation, 'reserve', 100, 'setup', 'model', 1, 2, 3)
                    """.trimIndent(),
                ).param("account", UUID.randomUUID())
                .param("reservation", UUID.randomUUID())
                .update()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `gloss keeps a turn's calls, the tools' answers, a recommendation and what it said`() {
        val account = account()
        val session = session(account)

        assertThatCode {
            listOf("tool_calls", "web_searched", "tool_refused", "recommend", "books_shown", "said", "books")
                .forEach { message(session = session, account = account, role = "gloss", kind = it) }
        }.doesNotThrowAnyException()
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a reader never says what the model does`() {
        val account = account()
        val session = session(account)

        assertThatThrownBy { message(session = session, account = account, role = "reader", kind = "recommend") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a web search is answered once, with its cost, and then kept as it is`() {
        val account = account()
        val id = search(account, session(account))

        answer(id = id, outcome = "answered", results = 5, cost = 7_000)

        assertThat(
            jdbc
                .sql("select cost_micros from web_search where id = :id")
                .param("id", id)
                .query(Long::class.java)
                .single(),
        ).isEqualTo(7_000)
        assertThatThrownBy { answer(id = id, outcome = "failed", results = null, cost = null) }
            .isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a web search is never deleted`() {
        val account = account()
        val id = search(account, session(account))

        assertThatThrownBy { jdbc.sql("delete from web_search where id = :id").param("id", id).update() }
            .isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a failed web search has no results and no cost`() {
        val account = account()
        val id = search(account, session(account))

        assertThatThrownBy { answer(id = id, outcome = "failed", results = 3, cost = 7_000) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a web search belongs to a session of the same reader`() {
        val account = account()
        val session = session(account)

        assertThatThrownBy { search(account(), session) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
