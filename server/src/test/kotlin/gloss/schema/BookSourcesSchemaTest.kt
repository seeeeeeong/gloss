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
class BookSourcesSchemaTest(
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
            .param("loginId", "o-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(
        source: String,
        ref: String? = null,
        url: String? = null,
    ) {
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, source_ref, source_url, created_at)
                values (:id, 0, :account, '오브젝트', '{}', :source, :ref, :url, now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("account", account())
            .param("source", source)
            .param("ref", ref)
            .param("url", url)
            .update()
    }

    private val page = "https://www.yes24.com/product/goods/101"

    // PostgreSQL ends a transaction at its first error; a savepoint lets one test try several rows.
    private fun assertRefused(insert: () -> Unit) {
        jdbc.sql("savepoint attempt").update()
        assertThatThrownBy(insert).isInstanceOf(DataIntegrityViolationException::class.java)
        jdbc.sql("rollback to savepoint attempt").update()
    }

    @Test
    fun `a book is typed in or from YES24, and no other source is known`() {
        assertThatCode { book("manual") }.doesNotThrowAnyException()
        assertThatCode { book("yes24", ref = "101", url = page) }.doesNotThrowAnyException()

        assertRefused { book("kakao", ref = "101", url = page) }
    }

    @Test
    fun `a sourced book has both its item and its page, and a typed one has neither`() {
        assertRefused { book("yes24", ref = "101") }
        assertRefused { book("yes24", url = page) }
        assertRefused { book("manual", ref = "101") }
        assertRefused { book("manual", url = page) }
    }

    @Test
    fun `an item and a page are held to a length`() {
        assertRefused { book("yes24", ref = "", url = page) }
        assertRefused { book("yes24", ref = "1".repeat(51), url = page) }
        assertRefused { book("yes24", ref = "101", url = "https://" + "a".repeat(500)) }
    }
}
