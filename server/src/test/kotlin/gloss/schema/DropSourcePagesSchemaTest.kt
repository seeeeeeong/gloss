package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class DropSourcePagesSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun run(migration: String) {
        jdbc.sql(ClassPathResource("db/migration/$migration").getContentAsString(Charsets.UTF_8)).update()
    }

    private fun tables(): List<String?> =
        jdbc
            .sql("select table_name from information_schema.tables where table_schema = current_schema()")
            .query(String::class.java)
            .list()

    private fun sessionColumns(): List<String?> =
        jdbc
            .sql("select column_name from information_schema.columns where table_name = 'setup_session'")
            .query(String::class.java)
            .list()

    // Contents held as found on a page: an older session, from before V23.
    private fun foundSession(): UUID {
        val account = UUID.randomUUID()
        val session = UUID.randomUUID()
        val page = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", account)
            .param("loginId", "d-" + account.toString().take(12))
            .update()
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, edition_ref, held_title, contents,
                                           created_at, updated_at)
                values (:id, 0, :account, 'book_chosen', '101', '운영체제', '1장 개관', now(), now())
                """.trimIndent(),
            ).param("id", session)
            .param("account", account)
            .update()
        jdbc
            .sql(
                """
                insert into source_page (id, account_id, session_id, url, title, fetched_at, text_sha256, characters)
                values (:id, :account, :session, 'https://example.com/toc', '목차', now(), :sha, 900)
                """.trimIndent(),
            ).param("id", page)
            .param("account", account)
            .param("session", session)
            .param("sha", "a".repeat(64))
            .update()
        jdbc
            .sql("update setup_session set contents_source_id = :page where id = :id")
            .param("page", page)
            .param("id", session)
            .update()
        return session
    }

    @Test
    fun `no page is kept, and a session names none`() {
        assertThat(tables()).contains("setup_session").doesNotContain("source_page")
        assertThat(sessionColumns()).contains("contents").doesNotContain("contents_source_id")
    }

    @Test
    fun `contents an older session found on a page stay held once the page is dropped`() {
        run("V21__source_pages.sql")
        val session = foundSession()

        run("V23__drop_source_pages.sql")

        assertThat(tables()).doesNotContain("source_page")
        assertThat(sessionColumns()).doesNotContain("contents_source_id")
        val contents =
            jdbc
                .sql("select contents from setup_session where id = :id")
                .param("id", session)
                .query(String::class.java)
                .single()
        assertThat(contents).isEqualTo("1장 개관")
    }
}
