package gloss.post.application

import gloss.TestcontainersConfiguration
import gloss.book.application.BookService
import gloss.book.application.dto.BookCreateRequest
import gloss.common.error.GlossException
import gloss.llm.application.LlmError
import gloss.post.application.dto.PostGenerationResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Rolled back with each test, so the confirm's event is never delivered: only what is asked for here is queued.
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class PostGenerationServiceTest(
    @Autowired private val postGenerationService: PostGenerationService,
    @Autowired private val postService: PostService,
    @Autowired private val bookService: BookService,
    @Autowired private val jdbc: JdbcClient,
) {
    /** A reader's book with confirmed contents: a chapter, two sections and an index. */
    private inner class Shelf {
        val account: UUID = UUID.randomUUID()
        val book: UUID
        val toc: UUID
        val keys: List<String>
        private val kinds = listOf("overview", "lesson", "lesson", "none")

        init {
            jdbc
                .sql(
                    """
                    insert into account (id, version, login_id, display_name, password_hash, role, status,
                                         must_change_password, session_epoch, created_at)
                    values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                    """.trimIndent(),
                ).param("id", account)
                .param("loginId", "g-" + account.toString().take(12))
                .update()
            val created =
                bookService.create(
                    account,
                    BookCreateRequest(title = "오브젝트", toc = "1장 객체, 설계\n1.1 티켓 판매\n1.2 무엇이 문제인가\n찾아보기"),
                )
            bookService.confirmToc(account, created.id, created.toc.id)
            book = created.id
            toc = created.toc.id
            keys = created.toc.items.map { it.itemKey }
        }

        /** A plan as the plan job would have kept it: the index has nothing to learn from. */
        fun plan(versionNo: Int = 1): UUID {
            val id = UUID.randomUUID()
            jdbc
                .sql(
                    """
                    insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                           json_sha256, model, prompt_version, created_at)
                    values (:id, 0, :account, :book, :toc, :versionNo, :json, repeat('b', 64), 'model-x', 'plan-1', now())
                    """.trimIndent(),
                ).param("id", id)
                .param("account", account)
                .param("book", book)
                .param("toc", toc)
                .param("versionNo", versionNo)
                .param("json", """{"items":[${kinds.joinToString(",") { """{"kind":"$it"}""" }}]}""")
                .update()
            return id
        }

        /** A post for the second item, as its job would have published it from a plan of its own. */
        fun publish() {
            postService.publish(
                PublishRequest(
                    accountId = account,
                    bookId = book,
                    itemKey = keys[1],
                    planId = plan(versionNo = 2),
                    contentMd = "## 학습 목표\n\n본문이다.",
                    finishReason = "stop",
                    model = "model-x",
                    promptVersion = "post-1",
                    tokens = PostTokens(cacheHit = 0, cacheMiss = 100, completion = 200),
                ),
            )
        }

        fun ask(
            key: String = keys[1],
            retry: Boolean = false,
            book: UUID = this.book,
        ) = postGenerationService.request(accountId = account, bookId = book, itemKey = key, retry = retry)

        fun jobs(): List<String?> =
            jdbc
                .sql(
                    """
                    select kind || ':' || state from generation_job
                    where account_id = :account order by created_at, id
                    """.trimIndent(),
                ).param("account", account)
                .query(String::class.java)
                .list()

        fun fail(errorKind: String) {
            jdbc
                .sql(
                    """
                    update generation_job set state = 'failed', error_kind = :error, finished_at = now()
                    where account_id = :account and state = 'queued'
                    """.trimIndent(),
                ).param("account", account)
                .param("error", errorKind)
                .update()
        }
    }

    @Test
    fun `a book without a plan gets its plan first, asked for once however often the reader asks`() {
        val shelf = Shelf()

        val first = shelf.ask()
        val again = shelf.ask(key = shelf.keys[2])

        assertThat(first).isEqualTo(PostGenerationResponse.PLANNING)
        assertThat(again).isEqualTo(PostGenerationResponse.PLANNING)
        assertThat(shelf.jobs()).containsExactly("plan:queued")
    }

    @Test
    fun `once the plan is kept the section's post is asked for, once`() {
        val shelf = Shelf()
        val plan = shelf.plan()

        val first = shelf.ask()
        val again = shelf.ask()

        assertThat(first).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(again).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(shelf.jobs()).containsExactly("post:queued")
        val job =
            jdbc
                .sql(
                    """
                    select dedupe_key, payload ->> 'bookId' as book, payload ->> 'planId' as plan,
                           payload ->> 'itemKey' as item
                    from generation_job where account_id = :account
                    """.trimIndent(),
                ).param("account", shelf.account)
                .query()
                .singleRow()
        assertThat(job.values)
            .containsExactly("post:$plan:${shelf.keys[1]}", shelf.book.toString(), plan.toString(), shelf.keys[1])
    }

    @Test
    fun `each section has its own job`() {
        val shelf = Shelf()
        shelf.plan()

        shelf.ask(key = shelf.keys[1])
        shelf.ask(key = shelf.keys[2])

        assertThat(shelf.jobs()).containsExactly("post:queued", "post:queued")
    }

    @Test
    fun `a plan that failed is told with its reason, and asked for again only on a retry`() {
        val shelf = Shelf()
        shelf.ask()
        shelf.fail("llm.no_key")

        val opened = shelf.ask()
        val retried = shelf.ask(retry = true)

        assertThat(opened)
            .isEqualTo(PostGenerationResponse.failed(code = "llm.no_key", message = LlmError.NO_KEY.message))
        assertThat(retried).isEqualTo(PostGenerationResponse.PLANNING)
        assertThat(shelf.jobs()).containsExactly("plan:failed", "plan:queued")
    }

    @Test
    fun `a post that failed is told, in general words when the reason is not one a reader acts on`() {
        val shelf = Shelf()
        shelf.plan()
        shelf.ask()
        shelf.fail("finish.length")

        val opened = shelf.ask()
        val retried = shelf.ask(retry = true)

        assertThat(opened.state).isEqualTo("failed")
        assertThat(opened.code).isEqualTo("finish.length")
        assertThat(opened.message).isEqualTo("글을 만들지 못했습니다. 다시 시도해 주세요")
        assertThat(retried).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(shelf.jobs()).containsExactly("post:failed", "post:queued")
    }

    @Test
    fun `a post that failed under an earlier plan says nothing of the section under the book's new one`() {
        val shelf = Shelf()
        shelf.plan()
        shelf.ask()
        shelf.fail("finish.length")
        shelf.plan(versionNo = 2)

        assertThat(shelf.ask()).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(shelf.jobs()).containsExactly("post:failed", "post:queued")
    }

    @Test
    fun `a book's new plan makes its posts of an earlier one stale, and a late word of an earlier plan does not`() {
        val shelf = Shelf()
        shelf.publish()
        val newer = shelf.plan(versionNo = 3)

        postGenerationService.outdatePosts(shelf.account, shelf.book, UUID.randomUUID())
        val afterLate = postService.freshness(shelf.account, shelf.book, shelf.keys[1])
        postGenerationService.outdatePosts(shelf.account, shelf.book, newer)

        assertThat(afterLate).isEqualTo(PostFreshness.FRESH)
        assertThat(postService.freshness(shelf.account, shelf.book, shelf.keys[1])).isEqualTo(PostFreshness.STALE)
    }

    @Test
    fun `a section the plan found nothing to learn from gets no post`() {
        val shelf = Shelf()
        shelf.plan()

        assertThat(shelf.ask(key = shelf.keys[3])).isEqualTo(PostGenerationResponse.NONE)
        assertThat(shelf.jobs()).isEmpty()
    }

    @Test
    fun `a section with a post is ready, and nothing more is asked for`() {
        val shelf = Shelf()
        shelf.publish()

        assertThat(shelf.ask()).isEqualTo(PostGenerationResponse.READY)
        assertThat(shelf.jobs()).isEmpty()
    }

    @Test
    fun `a post the book's newer plan made stale stays to read, and is written again only when asked to be`() {
        val shelf = Shelf()
        shelf.publish()
        shelf.plan()
        postService.outdate(shelf.account, shelf.book, UUID.randomUUID())

        val opened = shelf.ask()
        val asked = shelf.ask(retry = true)
        val again = shelf.ask()

        assertThat(opened).isEqualTo(PostGenerationResponse.READY)
        assertThat(asked).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(again).isEqualTo(PostGenerationResponse.WRITING)
        assertThat(shelf.jobs()).containsExactly("post:queued")
    }

    @Test
    fun `a rewrite that failed is told, not passed off as the post being ready`() {
        val shelf = Shelf()
        shelf.publish()
        shelf.plan()
        postService.outdate(shelf.account, shelf.book, UUID.randomUUID())
        shelf.ask(retry = true)
        shelf.fail("budget.limit_reached")

        assertThat(shelf.ask().state).isEqualTo("failed")
        assertThat(shelf.ask().code).isEqualTo("budget.limit_reached")
    }

    @Test
    fun `a section the contents in use do not have, or of another reader's book, is not found`() {
        val shelf = Shelf()
        val other = Shelf()

        listOf(
            { shelf.ask(key = "없는 항목") },
            { other.ask(book = shelf.book, key = shelf.keys[1]) },
            { shelf.ask(book = UUID.randomUUID()) },
        ).forEach { call ->
            assertThatThrownBy { call() }.isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(PostError.SECTION_NOT_FOUND)
            }
        }
        assertThat(shelf.jobs()).isEmpty()
    }
}
