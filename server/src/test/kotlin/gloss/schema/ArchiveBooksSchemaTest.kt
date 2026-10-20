package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ArchiveBooksSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private val isbn = "9788966262472"

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
            .param("loginId", "ab-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(
        account: UUID,
        archived: Boolean = false,
    ) = jdbc
        .sql(
            """
            insert into book (id, version, account_id, title, authors, isbn13, source, created_at, archived_at)
            values (:id, 0, :account, '오브젝트', '{}', :isbn, 'manual', now(),
                    case when :archived then now() end)
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("account", account)
        .param("isbn", isbn)
        .param("archived", archived)
        .update()

    @Test
    fun `a book taken off the shelf leaves its isbn free on it`() {
        val account = account()
        book(account, archived = true)

        assertThatCode { book(account) }.doesNotThrowAnyException()
    }

    @Test
    fun `an isbn is on a shelf once`() {
        val account = account()
        book(account)

        assertThatThrownBy { book(account) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
