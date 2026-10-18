package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// V23 drops the page table and the session's link to it; what V21 leaves is the kinds it allowed.
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class SourcePagesSchemaTest(
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
            .param("loginId", "p-" + id.toString().take(12))
            .update()
        return id
    }

    private fun session(account: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, edition_ref, held_title, contents,
                                           created_at, updated_at)
                values (:id, 0, :account, 'book_chosen', '101', '운영체제', 'Part 1 개관', now(), now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .update()
        return id
    }

    @Test
    fun `the reader's ask for another source and the pages gloss read stay allowed, as older sessions hold them`() {
        val account = account()
        val session = session(account)

        assertThatCode {
            listOf("reader" to "seek", "gloss" to "page_fetched").forEach { (role, kind) ->
                jdbc
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
            }
        }.doesNotThrowAnyException()
    }
}
