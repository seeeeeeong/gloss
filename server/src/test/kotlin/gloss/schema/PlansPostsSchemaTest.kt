package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
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
class PlansPostsSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    /** A reader's book with its contents, the ground a plan and a post stand on. */
    private inner class Shelf {
        val account: UUID = UUID.randomUUID()
        val book: UUID = UUID.randomUUID()
        val toc: UUID = UUID.randomUUID()

        init {
            jdbc
                .sql(
                    """
                    insert into account (id, version, login_id, display_name, password_hash, role, status,
                                         must_change_password, session_epoch, created_at)
                    values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                    """.trimIndent(),
                ).param("id", account)
                .param("loginId", "p-" + account.toString().take(12))
                .update()
            jdbc
                .sql(
                    """
                    insert into book (id, version, account_id, title, authors, source, created_at)
                    values (:id, 0, :account, '오브젝트', '{}', 'manual', now())
                    """.trimIndent(),
                ).param("id", book)
                .param("account", account)
                .update()
            jdbc
                .sql(
                    """
                    insert into toc (id, version, account_id, book_id, version_no, origin, raw, raw_sha256, status, created_at)
                    values (:id, 0, :account, :book, 1, 'manual', '1장', repeat('a', 64), 'draft', now())
                    """.trimIndent(),
                ).param("id", toc)
                .param("account", account)
                .param("book", book)
                .update()
        }
    }

    private fun plan(
        shelf: Shelf,
        versionNo: Int = 1,
        json: String = """{"items":[]}""",
        account: UUID = shelf.account,
        book: UUID = shelf.book,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                       json_sha256, model, prompt_version, created_at)
                values (:id, 0, :account, :book, :toc, :versionNo, :json, repeat('b', 64), 'model-x', 'p1', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("book", book)
            .param("toc", shelf.toc)
            .param("versionNo", versionNo)
            .param("json", json)
            .update()
        return id
    }

    private fun post(
        shelf: Shelf,
        itemKey: String = "1장 객체",
        account: UUID = shelf.account,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into post (id, version, account_id, book_id, item_key, stale, created_at)
                values (:id, 0, :account, :book, :itemKey, false, now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("book", shelf.book)
            .param("itemKey", itemKey)
            .update()
        return id
    }

    private data class Writing(
        val post: UUID,
        val plan: UUID,
        val account: UUID,
        val book: UUID,
        val versionNo: Int = 1,
        val content: String = "# 객체",
        val finishReason: String = "stop",
    )

    private fun writing(
        shelf: Shelf,
        post: UUID,
        plan: UUID,
    ) = Writing(post = post, plan = plan, account = shelf.account, book = shelf.book)

    private fun version(writing: Writing): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into post_version (id, version, account_id, book_id, post_id, version_no, plan_id, content_md,
                                          content_sha256, model, prompt_version, finish_reason, cache_hit_tokens,
                                          cache_miss_tokens, completion_tokens, generated_at)
                values (:id, 0, :account, :book, :post, :versionNo, :plan, :content, repeat('c', 64), 'model-x', 'p1',
                        :finishReason, 0, 0, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", writing.account)
            .param("book", writing.book)
            .param("post", writing.post)
            .param("versionNo", writing.versionNo)
            .param("plan", writing.plan)
            .param("content", writing.content)
            .param("finishReason", writing.finishReason)
            .update()
        return id
    }

    private fun show(
        post: UUID,
        version: UUID,
    ) = jdbc
        .sql("update post set current_version_id = :version where id = :id")
        .param("version", version)
        .param("id", post)
        .update()

    @Test
    fun `a post is written from a plan, version after version, and shows one of them`() {
        val shelf = Shelf()
        val post = post(shelf)
        val first = version(writing(shelf, post, plan(shelf)))
        val second = version(writing(shelf, post, plan(shelf, versionNo = 2)).copy(versionNo = 2))

        assertThatCode { show(post, first) }.doesNotThrowAnyException()
        assertThatCode { show(post, second) }.doesNotThrowAnyException()
    }

    @Test
    fun `a plan's version number is used once for a version of the contents`() {
        val shelf = Shelf()
        plan(shelf)

        assertThatThrownBy { plan(shelf) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a plan is for the book its contents belong to`() {
        val shelf = Shelf()
        val otherBook = Shelf().book

        assertThatThrownBy { plan(shelf, book = otherBook) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a plan belongs to the owner of its contents`() {
        val shelf = Shelf()
        val stranger = Shelf().account

        assertThatThrownBy { plan(shelf, account = stranger) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a plan is a JSON object`() {
        val shelf = Shelf()

        assertThatThrownBy { plan(shelf, json = "[]") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a plan that is not JSON is refused`() {
        val shelf = Shelf()

        assertThatThrownBy { plan(shelf, json = "{items") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a plan keeps its text byte for byte`() {
        val spaced = """{ "b": 1,  "a": [ ] }"""
        val id = plan(Shelf(), json = spaced)

        val kept =
            jdbc
                .sql("select canonical_json from book_plan where id = :id")
                .param("id", id)
                .query(String::class.java)
                .single()

        assertThat(kept).isEqualTo(spaced)
    }

    @Test
    fun `a plan is never changed`() {
        val id = plan(Shelf())

        assertThatThrownBy {
            jdbc.sql("update book_plan set model = 'other' where id = :id").param("id", id).update()
        }.hasMessageContaining("book_plan is append-only")
    }

    @Test
    fun `a plan is never deleted`() {
        val id = plan(Shelf())

        assertThatThrownBy {
            jdbc.sql("delete from book_plan where id = :id").param("id", id).update()
        }.hasMessageContaining("book_plan is append-only")
    }

    @Test
    fun `a section of a book has one post`() {
        val shelf = Shelf()
        post(shelf)

        assertThatThrownBy { post(shelf) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a post belongs to its book's owner`() {
        val shelf = Shelf()
        val stranger = Shelf().account

        assertThatThrownBy { post(shelf, account = stranger) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a post only shows a version of its own`() {
        val shelf = Shelf()
        val plan = plan(shelf)
        val post = post(shelf)
        val othersVersion = version(writing(shelf, post(shelf, itemKey = "2장"), plan))

        assertThatThrownBy { show(post, othersVersion) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version number is used once in a post`() {
        val shelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(shelf))
        version(writing)

        assertThatThrownBy { version(writing) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version belongs to its post's owner`() {
        val shelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(shelf))
        val stranger = Shelf().account

        assertThatThrownBy { version(writing.copy(account = stranger)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version is written from a plan of its own book`() {
        val shelf = Shelf()
        val otherShelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(otherShelf))

        assertThatThrownBy { version(writing) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version names the book of its post`() {
        val shelf = Shelf()
        val otherShelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(otherShelf))

        assertThatThrownBy { version(writing.copy(book = otherShelf.book)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `only an answer that finished is kept as a version`() {
        val shelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(shelf))

        assertThatThrownBy { version(writing.copy(finishReason = "length")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version's content has a limit`() {
        val shelf = Shelf()
        val writing = writing(shelf, post(shelf), plan(shelf))

        assertThatThrownBy { version(writing.copy(content = "가".repeat(200_001))) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a version is never changed`() {
        val shelf = Shelf()
        val id = version(writing(shelf, post(shelf), plan(shelf)))

        assertThatThrownBy {
            jdbc.sql("update post_version set content_md = '다른 글' where id = :id").param("id", id).update()
        }.hasMessageContaining("post_version is append-only")
    }

    @Test
    fun `a version is never deleted`() {
        val shelf = Shelf()
        val id = version(writing(shelf, post(shelf), plan(shelf)))

        assertThatThrownBy {
            jdbc.sql("delete from post_version where id = :id").param("id", id).update()
        }.hasMessageContaining("post_version is append-only")
    }
}
