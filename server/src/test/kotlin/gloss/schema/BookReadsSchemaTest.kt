package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
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
class BookReadsSchemaTest(
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
            .param("loginId", "br-" + id.toString().take(12))
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

    private fun read(
        account: UUID,
        book: UUID,
        itemKey: String = "1장 객체 > 1.1 티켓 판매",
    ) = jdbc
        .sql(
            """
            insert into book_read (book_id, account_id, item_key, read_at)
            values (:book, :account, :itemKey, now())
            """.trimIndent(),
        ).param("book", book)
        .param("account", account)
        .param("itemKey", itemKey)
        .update()

    private fun refused(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a book has one place, where its reader last was`() {
        val account = account()
        val book = book(account)
        read(account, book)

        refused { read(account, book, itemKey = "2장 설계") }
    }

    @Test
    fun `each of a reader's books has a place of its own`() {
        val account = account()

        read(account, book(account))
        read(account, book(account))

        val kept = jdbc.sql("select count(*) from book_read where account_id = :account").param("account", account)
        assertThat(kept.query(Int::class.java).single()).isEqualTo(2)
    }

    @Test
    fun `an item key is never empty`() {
        val account = account()

        refused { read(account, book(account), itemKey = "") }
    }

    @Test
    fun `an item key is at most 600 characters`() {
        val account = account()

        refused { read(account, book(account), itemKey = "가".repeat(601)) }
    }

    @Test
    fun `a place is in a book of its reader's own`() {
        val book = book(account())

        refused { read(account(), book) }
    }
}
