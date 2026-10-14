package gloss.post.application

import gloss.TestcontainersConfiguration
import gloss.apikey.FakeKeyCheckGateway
import gloss.book.application.BookService
import gloss.book.application.dto.BookCreateRequest
import gloss.book.application.dto.TocReviseRequest
import gloss.generation.application.JobRunner
import gloss.generation.application.RunningJob
import gloss.generation.infrastructure.JdbcJobQueue
import gloss.llm.FakeLlmGateway
import gloss.llm.KeyedReaders
import gloss.llm.domain.ChatResult
import gloss.llm.domain.TokenUsage
import gloss.llm.domain.chat.FinishReason
import gloss.llm.domain.chat.ReplyFormat
import gloss.llm.domain.chat.Role
import gloss.llm.domain.chat.Thinking
import gloss.plan.application.PlanView
import gloss.post.domain.PostPrompt
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Not transactional: the plan's job is asked for after a real commit, and a model call commits its own
// steps. Plans and posts are append-only, so the readers and books made here stay; each test makes its own.
@SpringBootTest
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeLlmGateway.Config::class,
    KeyedReaders.Config::class,
)
class PostJobHandlerTest(
    @Autowired private val bookService: BookService,
    @Autowired private val jobRunner: JobRunner,
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val readers: KeyedReaders,
) {
    // Only stamped on the job as when it started; due times and leases run on the database's clock.
    private val now = Instant.parse("2026-10-14T00:00:00Z")
    private val contents = "1장 객체, 설계\n1.1 티켓 판매 애플리케이션\n찾아보기"
    private val planReply =
        """{"audience":"객체지향을 처음 배우는 개발자","glossary":[],"items":[""" +
            """{"i":0,"kind":"overview","covers":["장의 흐름"],"excludes":[],"example":""},""" +
            """{"i":1,"kind":"lesson","covers":["의존성 문제"],"excludes":[],"example":"영화관"},""" +
            """{"i":2,"kind":"none","covers":[],"excludes":[],"example":""}]}"""
    private val written = "## 학습 목표\n\n- 의존성 문제를 설명한다.\n\n## 1. 티켓 판매\n\n본문이다.\n\n## 요약\n\n- 요약이다."
    private val usage =
        TokenUsage(cacheHitTokens = 700, cacheMissTokens = 200, completionTokens = 300, reasoningTokens = 0)

    private class Planned(
        val account: UUID,
        val bookId: UUID,
        val tocId: UUID,
        val keys: List<String>,
    )

    private data class Ending(
        val state: String,
        val errorKind: String?,
    )

    // The worker does not run in tests: each test claims the job it asked for, from a queue it emptied.
    @BeforeEach
    @AfterEach
    fun emptyQueue() {
        jdbc.sql("delete from generation_job").update()
        fakeLlmGateway.reset()
    }

    private fun answers(
        text: String,
        finish: FinishReason = FinishReason.STOP,
    ) {
        fakeLlmGateway.answer = { _, _ -> ChatResult(text, finish, usage) }
    }

    // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
    private fun awaitJob(account: UUID) {
        await().atMost(Duration.ofSeconds(10)).until {
            jdbc
                .sql("select count(*) from generation_job where account_id = :account and state = 'queued'")
                .param("account", account)
                .query(Int::class.java)
                .single() == 1
        }
    }

    private fun confirmed(account: UUID): Planned {
        val book =
            bookService.create(
                account,
                BookCreateRequest(title = "오브젝트", authors = listOf("조영호"), toc = contents),
            )
        bookService.confirmToc(account, book.id, book.toc.id)
        awaitJob(account)
        return Planned(
            account = account,
            bookId = book.id,
            tocId = book.toc.id,
            keys = book.toc.items.map { it.itemKey },
        )
    }

    /** A book whose plan is kept, by the plan's own job, and nothing left in the queue or the gateway's record. */
    private fun planned(): Planned {
        val book = confirmed(readers.create())
        answers(planReply)
        check(runNext().state == "succeeded")
        fakeLlmGateway.reset()
        return book
    }

    /** Asked under the book's plan, as a reader's request is, or under [planId] when a test says another. */
    private fun ask(
        book: Planned,
        key: String = book.keys[1],
        planId: UUID? = null,
    ) {
        val kept =
            jdbc
                .sql(
                    "select id from book_plan where toc_id = :toc",
                ).param("toc", book.tocId)
                .query(UUID::class.java)
        val plan =
            PlanView(
                id = planId ?: kept.single(),
                bookId = book.bookId,
                tocId = book.tocId,
                canonicalJson = "{}",
                kinds = emptyList(),
            )
        jdbcJobQueue.enqueue(PostJobHandler.jobFor(accountId = book.account, plan = plan, itemKey = key), now)
    }

    private fun runNext(): Ending {
        val job = RunningJob(checkNotNull(jdbcJobQueue.claim("worker-t", now, Duration.ofSeconds(60))))
        jobRunner.run(job)
        return jdbc
            .sql("select state, error_kind from generation_job where id = :id")
            .param("id", job.jobId)
            .query { rs, _ -> Ending(rs.getString(1), rs.getString(2)) }
            .single()
    }

    private fun posts(book: Planned): List<String?> =
        jdbc
            .sql(
                """
                select v.content_md from post p join post_version v on v.id = p.current_version_id
                where p.book_id = :book
                """.trimIndent(),
            ).param("book", book.bookId)
            .query(String::class.java)
            .list()

    @Test
    fun `a section's post is written from the plan in one call and becomes what the reader sees`() {
        val book = planned()
        ask(book)
        answers(written)

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        val sent = fakeLlmGateway.requests.single()
        assertThat(sent.maxTokens).isEqualTo(8_000)
        assertThat(sent.thinking).isEqualTo(Thinking.DISABLED)
        assertThat(sent.format).isEqualTo(ReplyFormat.TEXT)
        assertThat(sent.messages.map { it.role }).containsExactly(Role.SYSTEM, Role.USER)
        assertThat(sent.messages[0].content).isEqualTo(PostPrompt.SYSTEM)
        val plan =
            jdbc
                .sql("select id, canonical_json from book_plan where toc_id = :toc")
                .param("toc", book.tocId)
                .query()
                .singleRow()
        assertThat(sent.messages[1].content)
            .isEqualTo("${plan["canonical_json"]}\n\n작성 대상: i=1 (1.1 티켓 판매 애플리케이션)")
        assertThat(posts(book)).containsExactly(written)
        val version =
            jdbc
                .sql(
                    """
                    select v.plan_id, v.model, v.prompt_version, v.cache_hit_tokens, v.cache_miss_tokens,
                           v.completion_tokens, v.job_id is not null as by_a_job, p.item_key
                    from post p join post_version v on v.id = p.current_version_id where p.book_id = :book
                    """.trimIndent(),
                ).param("book", book.bookId)
                .query()
                .singleRow()
        assertThat(version.values)
            .containsExactly(plan["id"], "deepseek-flash", "post-1", 700, 200, 300, true, book.keys[1])
        val call =
            jdbc
                .sql(
                    """
                    select purpose, prompt_version, finish_reason from generation_call
                    where account_id = :account and purpose = 'post'
                    """.trimIndent(),
                ).param("account", book.account)
                .query()
                .singleRow()
        assertThat(call.values).containsExactly("post", "post-1", "stop")
    }

    @Test
    fun `an answer cut at its cap is not kept`() {
        val book = planned()
        ask(book)
        answers(written, finish = FinishReason.LENGTH)

        assertThat(runNext()).isEqualTo(Ending("failed", "finish.length"))
        assertThat(posts(book)).isEmpty()
    }

    @Test
    fun `an empty answer is not a post`() {
        val book = planned()
        ask(book)
        answers("  \n")

        assertThat(runNext()).isEqualTo(Ending("failed", "post.empty_reply"))
        assertThat(posts(book)).isEmpty()
    }

    @Test
    fun `a section with nothing to learn from is sent nowhere`() {
        val book = planned()
        ask(book, key = book.keys[2])

        assertThat(runNext()).isEqualTo(Ending("failed", "post.nothing_to_write"))
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `under a plan the book does not have, or for a section the contents no longer have, nothing is sent`() {
        val unplanned = confirmed(readers.create())
        jdbc.sql("delete from generation_job").update()
        ask(unplanned, planId = UUID.randomUUID())
        assertThat(runNext()).isEqualTo(Ending("failed", "post.plan_gone"))

        val book = planned()
        ask(book, planId = UUID.randomUUID())
        assertThat(runNext()).isEqualTo(Ending("failed", "post.plan_gone"))
        ask(book, key = "없는 항목")
        assertThat(runNext()).isEqualTo(Ending("failed", "post.section_gone"))
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `contents confirmed anew while the post is written leave it unkept`() {
        val book = planned()
        ask(book)
        fakeLlmGateway.answer = { _, _ ->
            val revised = bookService.reviseToc(book.account, book.bookId, TocReviseRequest("1장 다시 쓴 목차"))
            bookService.confirmToc(book.account, book.bookId, revised.toc.id)
            ChatResult(written, FinishReason.STOP, usage)
        }

        val ending = runNext()

        assertThat(ending.state).isEqualTo("succeeded")
        assertThat(posts(book)).isEmpty()
        // The newer contents' plan job, asked for after their confirm committed; emptied with the queue.
        awaitJob(book.account)
    }

    @Test
    fun `a post the book's newer plan made stale is written again as its next version`() {
        val book = planned()
        ask(book)
        answers(written)
        runNext()
        jdbc.sql("update post set stale = true where book_id = :book").param("book", book.bookId).update()
        ask(book)
        answers("## 학습 목표\n\n다시 쓴 본문이다.")

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(posts(book)).containsExactly("## 학습 목표\n\n다시 쓴 본문이다.")
        val post =
            jdbc
                .sql(
                    "select stale from post where book_id = :book",
                ).param("book", book.bookId)
                .query()
                .singleRow()
        assertThat(post.values).containsExactly(false)
    }

    @Test
    fun `a job for a section that has its post asks nothing more`() {
        val book = planned()
        ask(book)
        answers(written)
        runNext()
        ask(book)

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(posts(book)).hasSize(1)
    }
}
