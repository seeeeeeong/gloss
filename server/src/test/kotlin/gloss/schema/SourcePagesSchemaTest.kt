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
class SourcePagesSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private val sha = "a".repeat(64)

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

    // An edition held, or a book typed in when [edition] is false.
    private fun session(
        account: UUID,
        edition: Boolean = true,
    ): UUID {
        val id = UUID.randomUUID()
        val held = if (edition) "'101', null" else "null, '{}'::text[]"
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, edition_ref, typed_authors, held_title,
                                           contents, created_at, updated_at)
                values (:id, 0, :account, 'book_chosen', $held, '운영체제', 'Part 1 개관', now(), now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .update()
        return id
    }

    private fun page(
        account: UUID,
        session: UUID,
        url: String = "https://example.com/toc",
        sha256: String = sha,
        characters: Int = 900,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into source_page (id, account_id, session_id, url, title, fetched_at, text_sha256, characters)
                values (:id, :account, :session, :url, '목차', now(), :sha, :characters)
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("session", session)
            .param("url", url)
            .param("sha", sha256)
            .param("characters", characters)
            .update()
        return id
    }

    private fun hold(
        session: UUID,
        page: UUID,
    ) = jdbc
        .sql("update setup_session set contents_source_id = :page where id = :session")
        .param("page", page)
        .param("session", session)
        .update()

    @Test
    fun `a page keeps its address, title, when it was read and a hash of its text, never the text`() {
        val columns =
            jdbc
                .sql("select column_name from information_schema.columns where table_name = 'source_page'")
                .query(String::class.java)
                .list()

        assertThat(columns).containsExactlyInAnyOrder(
            "id",
            "account_id",
            "session_id",
            "url",
            "title",
            "fetched_at",
            "text_sha256",
            "characters",
        )
    }

    @Test
    fun `held contents name the page of their own session they were found on`() {
        val account = account()
        val session = session(account)
        val page = page(account, session)

        assertThatCode { hold(session, page) }.doesNotThrowAnyException()
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `contents never name another session's page`() {
        val account = account()
        val other = page(account, session(account))

        assertThatThrownBy { hold(session(account), other) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a book typed in has no page to name`() {
        val account = account()
        val typed = session(account, edition = false)
        val page = page(account, typed)

        assertThatThrownBy { hold(typed, page) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an https address is kept whatever the case of its scheme`() {
        val account = account()

        assertThatCode { page(account, session(account), url = "HTTPS://example.com/toc") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `only an https address is kept`() {
        val account = account()

        assertThatThrownBy { page(account, session(account), url = "http://example.com/toc") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `the hash is SHA-256 in lowercase hex`() {
        val account = account()

        assertThatThrownBy { page(account, session(account), sha256 = "A".repeat(64)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `no more is read of a page than the model is shown`() {
        val account = account()

        assertThatThrownBy { page(account, session(account), characters = 20_001) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a page is kept as it was written`() {
        val account = account()
        val page = page(account, session(account))

        assertThatThrownBy {
            jdbc.sql("update source_page set title = '다른 제목' where id = :id").param("id", page).update()
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `the reader asks for another source, and gloss keeps the pages it read`() {
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
