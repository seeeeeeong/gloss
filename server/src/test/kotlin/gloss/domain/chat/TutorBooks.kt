package gloss.domain.chat

import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.service.BookService
import gloss.domain.book.service.TocService
import gloss.domain.book.service.TypedBook
import gloss.domain.post.service.PostService
import gloss.domain.post.service.PostTokens
import gloss.domain.post.service.PublishRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/**
 * Books a reader can ask the tutor about: contents confirmed, a plan kept, posts written as a test
 * asks. Committed, as the services commit their own steps; append-only, so they stay.
 */
class TutorBooks(
    private val jdbc: JdbcClient,
    private val bookService: BookService,
    private val tocService: TocService,
    private val postService: PostService,
) {
    class Shelved(
        val account: UUID,
        val id: UUID,
        val planId: UUID?,
        val keys: List<String>,
    )

    fun create(
        account: UUID,
        planned: Boolean = true,
    ): Shelved {
        val typed = TypedBook.of(title = "오브젝트", authors = listOf("조영호"), toc = "1장 객체\n1.1 티켓 판매\n2장 설계")
        val confirmed = bookService.createConfirmed(account, typed, WritingStyle.DEFAULT)
        val outline = checkNotNull(tocService.outline(account, confirmed.bookId))
        val planId = if (planned) plan(Planned(account, confirmed.bookId, confirmed.tocId)) else null
        return Shelved(
            account = account,
            id = confirmed.bookId,
            planId = planId,
            keys = outline.items.map { it.itemKey },
        )
    }

    /** The item's post, as a reader would see it; returns its version. */
    fun write(
        book: Shelved,
        key: String,
        content: String,
    ): UUID =
        postService
            .publish(
                PublishRequest(
                    accountId = book.account,
                    bookId = book.id,
                    itemKey = key,
                    planId = checkNotNull(book.planId),
                    contentMd = content,
                    finishReason = "stop",
                    model = "model-x",
                    promptVersion = "post-1",
                    tokens = PostTokens(cacheHit = 0, cacheMiss = 1_600, completion = 1_700),
                ),
            ).versionId

    private data class Planned(
        val account: UUID,
        val book: UUID,
        val toc: UUID,
        val versionNo: Int = 1,
        val json: String = PLAN,
    )

    /** A newer plan for the same contents, as a rewrite of the plan would leave; it is the one in use. */
    fun replan(book: Shelved): UUID {
        val toc = checkNotNull(tocService.outline(book.account, book.id)).tocId
        return plan(Planned(account = book.account, book = book.id, toc = toc, versionNo = 2, json = REPLANNED))
    }

    private fun plan(planned: Planned): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                       json_sha256, model, prompt_version, created_at)
                values (:id, 0, :account, :book, :toc, :versionNo, :json, repeat('b', 64), 'model-x', 'plan-1', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", planned.account)
            .param("book", planned.book)
            .param("toc", planned.toc)
            .param("versionNo", planned.versionNo)
            .param("json", planned.json)
            .update()
        return id
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun tutorBooks(
            jdbc: JdbcClient,
            bookService: BookService,
            tocService: TocService,
            postService: PostService,
        ) = TutorBooks(jdbc = jdbc, bookService = bookService, tocService = tocService, postService = postService)
    }

    companion object {
        const val REPLANNED =
            """{"title":"오브젝트","audience":"초급 개발자","glossary":[],"items":[""" +
                """{"i":0,"kind":"overview"},{"i":1,"kind":"lesson"},{"i":2,"kind":"lesson"}]}"""

        const val PLAN =
            """{"title":"오브젝트","audience":"개발자","glossary":[],"items":[""" +
                """{"i":0,"kind":"overview"},{"i":1,"kind":"lesson"},{"i":2,"kind":"lesson"}]}"""
    }
}
