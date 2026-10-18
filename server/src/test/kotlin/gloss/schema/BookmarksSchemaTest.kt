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
class BookmarksSchemaTest(
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
            .param("loginId", "bm-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(account: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at)
                values (:id, 0, :account, '오브젝트', '{}', 'manual', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .update()
        return id
    }

    private class Shelved(
        val account: UUID,
        val book: UUID,
    )

    private fun shelved(): Shelved {
        val account = account()
        return Shelved(account, book(account))
    }

    private fun bookmark(
        at: Shelved,
        itemKey: String = "1장 객체 > 1.1 티켓 판매",
        quote: String? = "관람객의 가방",
    ) = jdbc
        .sql(
            """
            insert into bookmark (id, version, account_id, book_id, item_key, quote, created_at)
            values (:id, 0, :account, :book, :itemKey, :quote, now())
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("account", at.account)
        .param("book", at.book)
        .param("itemKey", itemKey)
        .param("quote", quote)
        .update()

    // Hangul in no repeating order, three bytes a character that compression cannot shrink.
    private fun varied(length: Int) = (0 until length).map { '가' + it * 7919 % 11172 }.joinToString("")

    private fun refused(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a phrase of an item, the item itself and the phrase in another item are three bookmarks`() {
        val at = shelved()

        assertThatCode {
            bookmark(at)
            bookmark(at, quote = null)
            bookmark(at, itemKey = "2장 설계")
        }.doesNotThrowAnyException()
    }

    @Test
    fun `the same phrase of an item is kept once`() {
        val at = shelved()
        bookmark(at)

        refused { bookmark(at) }
    }

    @Test
    fun `an item itself is kept once`() {
        val at = shelved()
        bookmark(at, quote = null)

        refused { bookmark(at, quote = null) }
    }

    @Test
    fun `the longest phrase of the longest key fits the index that keeps it once`() {
        val at = shelved()

        assertThatCode { bookmark(at, itemKey = varied(600), quote = varied(1000)) }.doesNotThrowAnyException()
    }

    @Test
    fun `an item key is never empty`() {
        refused { bookmark(shelved(), itemKey = "") }
    }

    @Test
    fun `an item key is at most 600 characters`() {
        refused { bookmark(shelved(), itemKey = varied(601)) }
    }

    @Test
    fun `a phrase is never empty`() {
        refused { bookmark(shelved(), quote = "") }
    }

    @Test
    fun `a phrase is at most 1000 characters`() {
        refused { bookmark(shelved(), quote = varied(1001)) }
    }

    @Test
    fun `a bookmark is on a book of its reader's own`() {
        val book = book(account())

        refused { bookmark(Shelved(account(), book)) }
    }
}
