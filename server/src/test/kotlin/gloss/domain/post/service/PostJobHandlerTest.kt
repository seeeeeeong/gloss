package gloss.domain.post.service

import gloss.TestcontainersConfiguration
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import gloss.domain.book.service.BookService
import gloss.domain.generation.infrastructure.JdbcJobQueue
import gloss.domain.generation.service.JobRunner
import gloss.domain.generation.service.RunningJob
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.plan.service.PlanView
import gloss.domain.post.domain.PostPrompt
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
    private val written = lesson("본문이다.")
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

    private data class Check(
        val writing: Int,
        val passed: Boolean,
        val failed: String,
        val finishReason: String,
    )

    private fun passed(writing: Int) = Check(writing = writing, passed = true, failed = "", finishReason = "stop")

    private fun failed(
        writing: Int,
        failed: String,
        finishReason: String = "stop",
    ) = Check(writing = writing, passed = false, failed = failed, finishReason = finishReason)

    /** A post in the form the prompt asks of a lesson, [body] under each numbered section. */
    private fun lesson(body: String) =
        listOf(
            "## 학습 목표\n\n- 의존성 문제를 설명한다.",
            "## 1. 티켓 판매\n\n$body",
            "## 2. 의존성\n\n$body",
            "## 3. 설계 고치기\n\n$body",
            "## 요약\n\n- 요약이다.",
            "## 핵심 용어\n\n- **의존성**: 한 객체가 다른 객체를 아는 것이다.",
        ).joinToString("\n\n")

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

    private fun providerFailed() =
        LlmCallFailed(LlmCallFailed.Kind.PROVIDER_ERROR, outputStarted = false, promptMayHaveBilled = false)

    /** The account's job waiting for another attempt is due now, not after its backoff. */
    private fun dueNow(book: Planned) {
        jdbc
            .sql("update generation_job set run_after = now() where account_id = :account and state = 'queued'")
            .param("account", book.account)
            .update()
    }

    /** One answer for each call, in turn. */
    private fun answersInTurn(vararg results: ChatResult) {
        val queue = ArrayDeque(results.toList())
        fakeLlmGateway.answer = { _, _ -> queue.removeFirst() }
    }

    private fun checks(book: Planned): List<Check> =
        jdbc
            .sql(
                """
                select writing, passed, array_to_string(failed_checks, ','), finish_reason from post_check
                where account_id = :account order by id
                """.trimIndent(),
            ).param("account", book.account)
            .query { rs, _ ->
                Check(
                    writing = rs.getInt(1),
                    passed = rs.getBoolean(2),
                    failed = rs.getString(3),
                    finishReason = rs.getString(4),
                )
            }.list()

    /** Each of the account's post calls, by its reservation: the ledger rows it has, reserve and ending. */
    private fun postReservations(book: Planned): Map<UUID, List<String>> =
        jdbc
            .sql("select reservation_id, kind from usage_ledger where account_id = :account and purpose = 'post'")
            .param("account", book.account)
            .query { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getString(2) }
            .list()
            .groupBy({ it.first }, { it.second })

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
        assertThat(checks(book)).containsExactly(passed(1))
        assertThat(postReservations(book).values).containsExactly(listOf("reserve", "settle"))
    }

    @Test
    fun `a post that fails its check is written once more, the same call, and kept when it passes`() {
        val book = planned()
        ask(book)
        answersInTurn(
            ChatResult("이 글은 의존성을 다룬다.\n\n본문이다.", FinishReason.STOP, usage),
            ChatResult(written, FinishReason.STOP, usage),
        )

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(fakeLlmGateway.requests[1]).isEqualTo(fakeLlmGateway.requests[0])
        assertThat(posts(book)).containsExactly(written)
        assertThat(checks(book))
            .containsExactly(failed(1, "opening,sections"), passed(2))
        assertThat(postReservations(book).values).hasSize(2).allSatisfy {
            assertThat(it).containsExactlyInAnyOrder("reserve", "settle")
        }
    }

    @Test
    fun `an answer cut at its cap is written once more, and a second cut leaves nothing kept`() {
        val book = planned()
        ask(book)
        answers(written, finish = FinishReason.LENGTH)

        assertThat(runNext()).isEqualTo(Ending("failed", "post.check_failed"))
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(posts(book)).isEmpty()
        assertThat(checks(book))
            .containsExactly(failed(1, "finish", finishReason = "length"), failed(2, "finish", finishReason = "length"))
        assertThat(postReservations(book).values).hasSize(2).allSatisfy {
            assertThat(it).containsExactlyInAnyOrder("reserve", "settle")
        }
    }

    @Test
    fun `an empty answer is not a post`() {
        val book = planned()
        ask(book)
        answersInTurn(
            ChatResult("  \n", FinishReason.STOP, usage),
            ChatResult("# 오브젝트\n\n본문이다.", FinishReason.CONTENT_FILTER, usage),
        )

        assertThat(runNext()).isEqualTo(Ending("failed", "post.check_failed"))
        assertThat(posts(book)).isEmpty()
        assertThat(checks(book))
            .containsExactly(
                failed(1, "empty"),
                failed(2, "finish,title,opening,sections", finishReason = "content_filter"),
            )
    }

    @Test
    fun `a call that fails on its way is not written again here, and its reservation still ends`() {
        val book = planned()
        ask(book)
        val calls = ArrayDeque(listOf(ChatResult("본문이다.", FinishReason.STOP, usage)))
        fakeLlmGateway.answer = { _, _ ->
            calls.removeFirstOrNull()
                ?: throw LlmCallFailed(
                    LlmCallFailed.Kind.PROVIDER_ERROR,
                    outputStarted = false,
                    promptMayHaveBilled = false,
                )
        }

        val ending = runNext()

        assertThat(ending.state).isEqualTo("queued")
        assertThat(ending.errorKind).isEqualTo("llm.provider_failed")
        assertThat(posts(book)).isEmpty()
        assertThat(checks(book)).containsExactly(failed(1, "opening,sections"))
        assertThat(postReservations(book).values)
            .containsExactlyInAnyOrder(listOf("reserve", "settle"), listOf("reserve", "release"))
    }

    @Test
    fun `a writing whose call failed on its way is spent, so a retry writes only what is left`() {
        val book = planned()
        ask(book)
        val first = ArrayDeque(listOf(ChatResult("본문이다.", FinishReason.STOP, usage)))
        fakeLlmGateway.answer = { _, _ -> first.removeFirstOrNull() ?: throw providerFailed() }
        check(runNext().state == "queued")
        dueNow(book)
        answers(written)

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("failed", "post.check_failed"))
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(posts(book)).isEmpty()
        assertThat(checks(book)).containsExactly(failed(1, "opening,sections"))
    }

    @Test
    fun `a retry after a first call failed on its way writes once more, as the job's second writing`() {
        val book = planned()
        ask(book)
        fakeLlmGateway.answer = { _, _ -> throw providerFailed() }
        check(runNext().state == "queued")
        dueNow(book)
        answers(written)

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(posts(book)).containsExactly(written)
        assertThat(checks(book)).containsExactly(passed(2))
    }

    @Test
    fun `an attempt that lost its job to another takes no writing, so the two write twice in all`() {
        val book = planned()
        ask(book)
        val first = RunningJob(checkNotNull(jdbcJobQueue.claim("worker-a", now, Duration.ofSeconds(60))))
        var second: RunningJob? = null
        fakeLlmGateway.answer = { _, _ ->
            // The reaper hands the job on while the first attempt's call runs, before it sees it lost.
            jdbc
                .sql(
                    """
                    update generation_job set state = 'queued', lease_owner = null, lease_expires_at = null,
                                              run_after = now() where id = :id
                    """.trimIndent(),
                ).param("id", first.jobId)
                .update()
            second = jdbcJobQueue.claim("worker-b", now, Duration.ofSeconds(60))?.let(::RunningJob)
            ChatResult("본문이다.", FinishReason.STOP, usage)
        }

        jobRunner.run(first)
        answers(written)
        jobRunner.run(checkNotNull(second))

        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(posts(book)).containsExactly(written)
        assertThat(checks(book)).containsExactly(failed(1, "opening,sections"), passed(2))
        val taken =
            jdbc
                .sql("select writing, attempt from post_writing where account_id = :account order by writing")
                .param("account", book.account)
                .query { rs, _ -> rs.getInt(1) to rs.getInt(2) }
                .list()
        assertThat(taken).containsExactly(1 to 1, 2 to 2)
    }

    @Test
    fun `another account's writings under the same job id count for nothing`() {
        val book = planned()
        ask(book)
        val job =
            jdbc
                .sql("select id from generation_job where account_id = :account and kind = 'post'")
                .param("account", book.account)
                .query(UUID::class.java)
                .single()
        val other = UUID.randomUUID()
        (1..2).forEach { writing ->
            jdbc
                .sql(
                    """
                    insert into post_writing (account_id, job_id, writing, attempt, claimed_at)
                    values (:other, :job, :writing, 1, now())
                    """.trimIndent(),
                ).param("other", other)
                .param("job", job)
                .param("writing", writing)
                .update()
        }
        answers(written)

        assertThat(runNext()).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(checks(book)).containsExactly(passed(1))
    }

    @Test
    fun `contents confirmed anew before a rewrite leave it unwritten`() {
        val book = planned()
        ask(book)
        fakeLlmGateway.answer = { _, _ ->
            val revised = bookService.reviseToc(book.account, book.bookId, TocReviseRequest("1장 다시 쓴 목차"))
            bookService.confirmToc(book.account, book.bookId, revised.toc.id)
            ChatResult("본문이다.", FinishReason.STOP, usage)
        }

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(posts(book)).isEmpty()
        assertThat(checks(book)).containsExactly(failed(1, "opening,sections"))
        awaitJob(book.account)
    }

    @Test
    fun `a writing that passed but the database could not hold is not kept`() {
        val book = planned()
        ask(book)
        answers(lesson("본문이다.\u0000"))

        assertThat(runNext()).isEqualTo(Ending("failed", "post.unstorable_reply"))
        assertThat(fakeLlmGateway.requests).hasSize(1)
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
        answers(lesson("다시 쓴 본문이다."))

        val ending = runNext()

        assertThat(ending).isEqualTo(Ending("succeeded", null))
        assertThat(posts(book)).containsExactly(lesson("다시 쓴 본문이다."))
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
