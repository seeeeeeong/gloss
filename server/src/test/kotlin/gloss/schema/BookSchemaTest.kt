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
class BookSchemaTest(
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
            .param("loginId", "s-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(
        account: UUID,
        isbn13: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, isbn13, source, created_at)
                values (:id, 0, :account, '오브젝트', '{}', :isbn, 'manual', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("isbn", isbn13)
            .update()
        return id
    }

    private fun toc(
        account: UUID,
        book: UUID,
        versionNo: Int = 1,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into toc (id, version, account_id, book_id, version_no, origin, raw, raw_sha256, status, created_at)
                values (:id, 0, :account, :book, :versionNo, 'manual', '1장', repeat('a', 64), 'draft', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("book", book)
            .param("versionNo", versionNo)
            .update()
        return id
    }

    private fun item(
        account: UUID,
        toc: UUID,
        parent: UUID? = null,
        position: Int = 0,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into toc_item (id, version, account_id, toc_id, parent_id, position, ord, depth, title, item_key)
                values (:id, 0, :account, :toc, :parent, :position, :ord, :depth, '절', :key)
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("toc", toc)
            .param("parent", parent)
            .param("position", position)
            .param("ord", (if (parent == null) 0 else 1) + position * 2)
            .param("depth", if (parent == null) 1 else 2)
            .param("key", id.toString())
            .update()
        return id
    }

    @Test
    fun `a shelf holds one copy of an isbn, beside books without one`() {
        val account = account()
        book(account, isbn13 = "9788966262472")
        book(account)
        book(account)

        assertThatThrownBy { book(account, isbn13 = "9788966262472") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version number is used once in a book`() {
        val account = account()
        val book = book(account)
        toc(account, book)

        assertThatThrownBy { toc(account, book) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version belongs to its book's owner`() {
        val book = book(account())
        val stranger = account()

        assertThatThrownBy { toc(stranger, book) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a book only uses a version of its own contents`() {
        val account = account()
        val book = book(account)
        val othersToc = toc(account, book(account))

        assertThatThrownBy {
            jdbc
                .sql("update book set current_toc_id = :toc where id = :id")
                .param("toc", othersToc)
                .param("id", book)
                .update()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a draft is confirmed once, and stays confirmed`() {
        val account = account()
        val toc = toc(account, book(account))

        assertThatCode { jdbc.sql(CONFIRM).param("id", toc).update() }.doesNotThrowAnyException()
        assertThatThrownBy { jdbc.sql(CONFIRM).param("id", toc).update() }.hasMessageContaining(ONLY_CONFIRMS)
    }

    @Test
    fun `a draft's text is not changed in place`() {
        val account = account()
        val toc = toc(account, book(account))

        assertThatThrownBy { jdbc.sql("update toc set raw = '2장' where id = :id").param("id", toc).update() }
            .hasMessageContaining(ONLY_CONFIRMS)
    }

    @Test
    fun `a version is never removed`() {
        val account = account()
        val toc = toc(account, book(account))

        assertThatThrownBy { jdbc.sql("delete from toc where id = :id").param("id", toc).update() }
            .hasMessageContaining("toc is kept")
    }

    @Test
    fun `an item's place among its siblings is its own`() {
        val account = account()
        val toc = toc(account, book(account))
        val root = item(account, toc)
        item(account, toc, parent = root)

        assertThatThrownBy {
            item(
                account,
                toc,
                parent = root,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an item's parent is in its own version`() {
        val account = account()
        val book = book(account)
        val root = item(account, toc(account, book))
        val otherToc = toc(account, book, versionNo = 2)

        assertThatThrownBy { item(account, otherToc, parent = root) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an item is not changed`() {
        val account = account()
        val item = item(account, toc(account, book(account)))

        assertThatThrownBy {
            jdbc.sql("update toc_item set title = '바뀐 절' where id = :id").param("id", item).update()
        }.hasMessageContaining(APPEND_ONLY)
    }

    @Test
    fun `an item is not removed`() {
        val account = account()
        val item = item(account, toc(account, book(account)))

        assertThatThrownBy { jdbc.sql("delete from toc_item where id = :id").param("id", item).update() }
            .hasMessageContaining(APPEND_ONLY)
    }

    private companion object {
        const val CONFIRM =
            "update toc set status = 'confirmed', confirmed_at = now(), version = version + 1 where id = :id"
        const val ONLY_CONFIRMS = "a toc only goes from draft to confirmed"
        const val APPEND_ONLY = "toc_item is append-only"
    }
}
