package gloss.domain.plan.service

import gloss.TestcontainersConfiguration
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import gloss.domain.book.service.BookService
import gloss.domain.book.service.TypedBook
import gloss.domain.generation.infrastructure.JdbcJobQueue
import gloss.domain.generation.service.JobRunner
import gloss.domain.generation.service.RunningJob
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.plan.domain.PlanPrompt
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

// Not transactional: the job is asked for after a real commit, and a model call commits its own steps.
// A plan is append-only, so the readers, books and plans made here stay; each test makes its own.
@SpringBootTest
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeLlmGateway.Config::class,
    KeyedReaders.Config::class,
)
class PlanJobHandlerTest(
    @Autowired private val bookService: BookService,
    @Autowired private val jobRunner: JobRunner,
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val readers: KeyedReaders,
) {
    // Only stamped on the job as when it started; due times and leases run on the database's clock.
    private val now = Instant.parse("2026-10-13T00:00:00Z")
    private val contents = "1장 객체, 설계\n1.1 티켓 판매 애플리케이션\n1.2 무엇이 문제인가\n2장 객체지향 프로그래밍"
    private val usage =
        TokenUsage(cacheHitTokens = 0, cacheMissTokens = 900, completionTokens = 300, reasoningTokens = 0)

    private fun entry(
        i: Int,
        kind: String = "lesson",
    ) = """{"i":$i,"kind":"$kind","covers":["다루는 것 $i"],"excludes":[],"example":""}"""

    private fun reply(vararg entries: String) =
        """{"audience":"객체지향을 처음 배우는 개발자","glossary":[],"items":[${entries.joinToString(",")}]}"""

    private class Confirmed(
        val account: UUID,
        val bookId: UUID,
        val tocId: UUID,
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

    private fun queued(account: UUID): Int =
        jdbc
            .sql("select count(*) from generation_job where account_id = :account and state = 'queued'")
            .param("account", account)
            .query(Int::class.java)
            .single()

    // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
    private fun awaitJobs(
        account: UUID,
        count: Int,
    ) {
        await().atMost(Duration.ofSeconds(10)).until { queued(account) == count }
    }

    private fun confirmed(
        account: UUID,
        toc: String = contents,
    ): Confirmed {
        val book = bookService.create(account, BookCreateRequest(title = "오브젝트", authors = listOf("조영호"), toc = toc))
        bookService.confirmToc(account, book.id, book.toc.id)
        awaitJobs(account, 1)
        return Confirmed(account = account, bookId = book.id, tocId = book.toc.id)
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

    private fun plans(tocId: UUID): List<String?> =
        jdbc
            .sql("select canonical_json from book_plan where toc_id = :toc")
            .param("toc", tocId)
            .query(String::class.java)
            .list()

    @Test
    fun `confirmed contents are planned in one call, and the plan is kept as the bytes posts will carry`() {
        val book = confirmed(readers.create())
        fakeLlmGateway.answer = { _, _ ->
            ChatResult(reply(entry(0, "overview"), entry(1), entry(2), entry(3)), FinishReason.STOP, usage)
        }

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        val sent = fakeLlmGateway.requests.single()
        assertThat(sent.maxTokens).isEqualTo(5_200)
        assertThat(sent.thinking).isEqualTo(Thinking.DISABLED)
        assertThat(sent.format).isEqualTo(ReplyFormat.JSON)
        assertThat(sent.messages.map { it.role }).containsExactly(Role.SYSTEM, Role.USER)
        assertThat(sent.messages[0].content).isEqualTo(PlanPrompt.SYSTEM)
        assertThat(sent.messages[1].content).startsWith(
            "책: 오브젝트\n저자: 조영호\n수준: 전공. ${PlanPrompt.Level.COURSE.rule}\n문체: 교과서체\n\n" +
                "목차\n0\t-\t1\t1장\t객체, 설계\n1\t0\t2\t1.1\t",
        )
        assertThat(plans(book.tocId)).containsExactly(
            """{"book":{"title":"오브젝트","authors":["조영호"]},""" +
                """"style":{"level":{"name":"전공","rule":"${PlanPrompt.Level.COURSE.rule}"},""" +
                """"voice":{"name":"교과서체","rule":"${PlanPrompt.Voice.TEXTBOOK.rule}"}},""" +
                """"audience":"객체지향을 처음 배우는 개발자","glossary":[],""" +
                """"items":[{"i":0,"parent":null,"number":"1장","title":"객체, 설계","kind":"overview",""" +
                """"covers":["다루는 것 0"],"excludes":[],"example":null},""" +
                """{"i":1,"parent":0,"number":"1.1","title":"티켓 판매 애플리케이션","kind":"lesson",""" +
                """"covers":["다루는 것 1"],"excludes":[],"example":null},""" +
                """{"i":2,"parent":0,"number":"1.2","title":"무엇이 문제인가","kind":"lesson",""" +
                """"covers":["다루는 것 2"],"excludes":[],"example":null},""" +
                """{"i":3,"parent":null,"number":"2장","title":"객체지향 프로그래밍","kind":"lesson",""" +
                """"covers":["다루는 것 3"],"excludes":[],"example":null}]}""",
        )
        val call =
            jdbc
                .sql(
                    """
                    select purpose, prompt_version, finish_reason, cache_miss_tokens, completion_tokens
                    from generation_call where account_id = :account
                    """.trimIndent(),
                ).param("account", book.account)
                .query()
                .singleRow()
        assertThat(call.values).containsExactly("plan", "plan-2", "stop", 900L, 300L)
        val kept =
            jdbc
                .sql("select model, prompt_version, version_no from book_plan where toc_id = :toc")
                .param("toc", book.tocId)
                .query()
                .singleRow()
        assertThat(kept.values).containsExactly("deepseek-flash", "plan-2", 1)
    }

    @Test
    fun `front and back matter is left out of the plan, and the cap is of the items planned`() {
        val book = confirmed(readers.create(), toc = "들어가며\n1장 객체, 설계\n1.1 티켓 판매 애플리케이션\n2장 역할\n찾아보기")
        fakeLlmGateway.answer = { _, _ ->
            ChatResult(reply(entry(0, "none"), entry(1, "overview"), entry(2), entry(3)), FinishReason.STOP, usage)
        }

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        val sent = fakeLlmGateway.requests.single()
        assertThat(sent.maxTokens).isEqualTo(4_900)
        assertThat(sent.messages[1].content.substringAfter("목차\n")).isEqualTo(
            "1\t-\t1\t1장\t객체, 설계\n2\t1\t2\t1.1\t티켓 판매 애플리케이션\n3\t-\t1\t2장\t역할\n",
        )
        val kept = checkNotNull(plans(book.tocId).single())
        assertThat(Regex(""""i":(\d+)""").findAll(kept).map { it.groupValues[1] }.toList())
            .containsExactly("1", "2", "3")
        assertThat(kept).doesNotContain("들어가며").doesNotContain("찾아보기")
    }

    @Test
    fun `a book started in the level and voice the reader chose is planned for them, and its plan carries both`() {
        val account = readers.create()
        val typed = TypedBook.of(title = "오브젝트", authors = emptyList(), toc = contents)
        val style = WritingStyle(WritingStyle.Level.INTRO, WritingStyle.Voice.EXPLAIN)
        val book = bookService.createConfirmed(account, typed, style)
        awaitJobs(account, 1)
        fakeLlmGateway.answer = { _, _ ->
            ChatResult(reply(entry(0, "overview"), entry(1), entry(2), entry(3)), FinishReason.STOP, usage)
        }

        runNext()

        val sent = fakeLlmGateway.requests.single()
        assertThat(sent.messages[1].content)
            .contains("수준: 입문. ${PlanPrompt.Level.INTRO.rule}\n문체: 설명체\n")
        assertThat(plans(book.tocId).single())
            .contains(""""style":{"level":{"name":"입문",""")
            .contains(""""voice":{"name":"설명체","rule":"${PlanPrompt.Voice.EXPLAIN.rule}"}""")
    }

    @Test
    fun `a reply that leaves an item out is not kept, and the job fails for good`() {
        val book = confirmed(readers.create())
        fakeLlmGateway.answer = { _, _ -> ChatResult(reply(entry(0), entry(1), entry(3)), FinishReason.STOP, usage) }

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("failed", "plan.missing_item"))
        assertThat(plans(book.tocId)).isEmpty()
    }

    @Test
    fun `a reply that is not JSON is not kept`() {
        val book = confirmed(readers.create())
        fakeLlmGateway.answer = { _, _ -> ChatResult("목차를 보니 다음과 같습니다", FinishReason.STOP, usage) }

        assertThat(runNext()).isEqualTo(Ending("failed", "plan.not_json"))
        assertThat(plans(book.tocId)).isEmpty()
    }

    @Test
    fun `an empty reply is asked for once more`() {
        val book = confirmed(readers.create())
        fakeLlmGateway.answer = { _, _ -> ChatResult("  ", FinishReason.STOP, usage) }

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("queued", "plan.empty_reply"))
        assertThat(plans(book.tocId)).isEmpty()
    }

    @Test
    fun `a reader without a key is sent nothing, and the job says why`() {
        confirmed(readers.create(key = false))

        assertThat(runNext()).isEqualTo(Ending("failed", "llm.no_key"))
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `contents confirmed anew leave the older job nothing to write, and it asks nothing`() {
        val account = readers.create()
        val book = confirmed(account)
        val revised = bookService.reviseToc(account, book.bookId, TocReviseRequest("1장 다시 쓴 목차"))
        bookService.confirmToc(account, book.bookId, revised.toc.id)
        awaitJobs(account, 2)

        assertThat(runNext()).isEqualTo(Ending("failed", "plan.superseded"))
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `contents confirmed anew while the plan is written leave it unkept`() {
        val account = readers.create()
        val book = confirmed(account)
        fakeLlmGateway.answer = { _, _ ->
            val revised = bookService.reviseToc(account, book.bookId, TocReviseRequest("1장 다시 쓴 목차"))
            bookService.confirmToc(account, book.bookId, revised.toc.id)
            ChatResult(reply(entry(0), entry(1), entry(2), entry(3)), FinishReason.STOP, usage)
        }

        val ending = runNext()

        assertThat(ending.state).isEqualTo("succeeded")
        assertThat(plans(book.tocId)).isEmpty()
        // The newer contents' own job, asked for after their confirm committed; emptied with the queue.
        awaitJobs(account, 1)
    }

    @Test
    fun `contents longer than one call can plan are not sent`() {
        confirmed(readers.create(), toc = (1..401).joinToString("\n") { "$it 항목 $it" })

        assertThat(runNext()).isEqualTo(Ending("failed", "plan.too_many_items"))
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `a job for contents that have their plan asks nothing more`() {
        val book = confirmed(readers.create())
        fakeLlmGateway.answer = { _, _ ->
            ChatResult(reply(entry(0), entry(1), entry(2), entry(3)), FinishReason.STOP, usage)
        }
        runNext()
        jdbcJobQueue.enqueue(PlanJobHandler.jobFor(book.account, book.bookId, book.tocId), now)

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(plans(book.tocId)).hasSize(1)
    }
}
