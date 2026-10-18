package gloss.domain.setup.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.budget.dto.request.LimitRequest
import gloss.domain.budget.service.BudgetError
import gloss.domain.budget.service.BudgetService
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatRequest
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.service.LlmError
import gloss.domain.setup.FakePageGateway
import gloss.domain.setup.FakeWebSearchGateway
import gloss.domain.setup.SetupFakes
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.FetchedPage
import gloss.domain.setup.domain.PageFetchFailed
import gloss.domain.setup.domain.PageFetched
import gloss.domain.setup.domain.ReaderSought
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.WebPage
import gloss.domain.setup.dto.request.SetupConfirmRequest
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.request.SetupPickRequest
import gloss.domain.setup.dto.request.SetupSeekRequest
import gloss.domain.setup.dto.response.SetupSessionResponse
import gloss.domain.setup.dto.response.SetupSourcePageResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.UUID

// Committed, as the model's calls commit their own reservations: a session's messages are
// append-only, so its rows stay.
@SpringBootTest
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeBookSourceGateway.Config::class,
    FakeLlmGateway.Config::class,
    FakeWebSearchGateway.Config::class,
    FakePageGateway.Config::class,
    KeyedReaders.Config::class,
    MutableClock.Config::class,
    SetupFakes.Config::class,
)
class SetupContentsTurnServiceTest(
    @Autowired private val setupMessageService: SetupMessageService,
    @Autowired private val setupSessionService: SetupSessionService,
    @Autowired private val setupFakes: SetupFakes,
    @Autowired private val keyedReaders: KeyedReaders,
    @Autowired private val budgetService: BudgetService,
    @Autowired private val jdbc: JdbcClient,
) {
    private val json = JsonMapper()
    private val fakeLlmGateway = setupFakes.model
    private val fakeBookSourceGateway = setupFakes.books
    private val fakeWebSearchGateway = setupFakes.web
    private val fakePageGateway = setupFakes.pages
    private val clock = setupFakes.clock

    @BeforeEach
    fun reset() {
        setupFakes.reset()
        fakeWebSearchGateway.pages = listOf(page(NOTES, "운영체제 목차 정리"))
        fakePageGateway.pages[NOTES] = FetchedPage(url = NOTES, title = "운영체제 10판 목차 정리", text = PAGE)
    }

    private fun page(
        url: String,
        title: String,
    ) = WebPage(url = url, title = title, published = null, highlights = listOf("목차"))

    /** The model's replies, one a call, in order; a call past them fails the test. */
    private fun script(vararg replies: (ChatRequest) -> ChatResult) {
        val left = ArrayDeque(replies.toList())
        fakeLlmGateway.answer = { _, _ -> left.removeFirst()(fakeLlmGateway.requests.last()) }
    }

    private fun calls(vararg calls: Pair<String, Any>): (ChatRequest) -> ChatResult =
        { request ->
            val made =
                calls.mapIndexed { i, (name, arguments) ->
                    ToolCall(
                        id = "call-${request.messages.size}-$i",
                        name = name,
                        arguments = json.writeValueAsString(arguments),
                    )
                }
            ChatResult(text = "", finishReason = FinishReason.TOOL_CALLS, usage = USAGE, toolCalls = made)
        }

    private fun says(text: String): (ChatRequest) -> ChatResult =
        { ChatResult(text = text, finishReason = FinishReason.STOP, usage = USAGE) }

    private val search = calls("search_web" to mapOf("query" to "운영체제 10판 목차"))
    private val fetch = calls("fetch_page" to mapOf("url" to NOTES))

    private fun submit(lines: List<String> = DETAILED) =
        calls("submit_toc" to mapOf("source_url" to NOTES, "lines" to lines))

    /** A session whose search showed the edition, with the bookshop's [contents] for it. */
    private fun shown(
        reader: UUID,
        contents: String = PARTS,
    ): SetupSessionResponse {
        fakeBookSourceGateway.holds(ref = "101", title = "운영체제", contents = contents)
        script(calls("find_books" to mapOf("query" to "운영체제")), calls("show_books" to mapOf("query" to "운영체제")))
        return setupMessageService.start(reader, SetupMessageRequest("운영체제"))
    }

    private fun pick(
        reader: UUID,
        session: SetupSessionResponse,
    ) = setupMessageService.pick(reader, session.id, SetupPickRequest("101", session.revision))

    private fun kinds(session: SetupSessionResponse) = session.messages.map { it.kind }

    private fun ledger(account: UUID): List<Pair<String, String?>> =
        jdbc
            .sql("select kind, outcome from usage_ledger where account_id = :account order by id")
            .param("account", account)
            .query { row, _ -> row.getString("kind") to row.getString("outcome") }
            .list()

    private fun kept(sessionId: UUID): String =
        jdbc
            .sql("select string_agg(payload::text, ' ') from setup_message where session_id = :session")
            .param("session", sessionId)
            .query(String::class.java)
            .single()

    @Test
    fun `a coarse pick is looked up on the web, and contents checked against the page replace it with their source`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, fetch, submit())

        val found = pick(reader, session)

        assertThat(kinds(found).takeLast(8)).containsExactly(
            "pick",
            "contents",
            "tool_calls",
            "web_searched",
            "tool_calls",
            "page_fetched",
            "tool_calls",
            "contents",
        )
        assertThat(found.messages.first { it.kind == "contents" }.payload)
            .isEqualTo(ContentsBrought(ref = "101", title = "운영체제", items = 3, typed = false, thin = true))
        assertThat(found.messages.last().payload).isEqualTo(
            ContentsBrought(
                ref = "101",
                title = "운영체제",
                items = DETAILED.size,
                typed = false,
                sourceUrl = NOTES,
                sourceTitle = "운영체제 10판 목차 정리",
            ),
        )
        assertThat(found.messages.single { it.kind == "page_fetched" }.payload)
            .isEqualTo(
                PageFetched(callId = "call-4-0", url = NOTES, title = "운영체제 10판 목차 정리", characters = PAGE.length),
            )
        val held = checkNotNull(found.held)
        assertThat(held.raw).isEqualTo(DETAILED.joinToString("\n"))
        assertThat(held.items).hasSize(DETAILED.size)
        assertThat(held.found).isEqualTo(SetupSourcePageResponse(url = NOTES, title = "운영체제 10판 목차 정리"))
        assertThat(held.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(found.searching).isFalse()
        assertThat(found.state).isEqualTo("book_chosen")
        assertThat(fakeWebSearchGateway.asked).containsExactly("운영체제 10판 목차")
        assertThat(fakePageGateway.asked).containsExactly(NOTES)
    }

    @Test
    fun `every call is reserved and settled, with thinking off, a constant prompt and the cap stated`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, fetch, submit())

        pick(reader, session)

        val requests = fakeLlmGateway.requests.drop(2)
        assertThat(requests).hasSize(3)
        assertThat(requests).allSatisfy {
            assertThat(it.thinking).isEqualTo(Thinking.DISABLED)
            assertThat(it.maxTokens).isEqualTo(8_000)
            assertThat(it.toolChoice).isEqualTo(ToolChoice.Auto)
            assertThat(it.tools.map { tool -> tool.name }).containsExactly("search_web", "fetch_page", "submit_toc")
            assertThat(it.messages.first().content).isEqualTo(SetupPrompt.CONTENTS_SYSTEM)
        }
        // The book and the bookshop's parts go after the system prompt, never into it.
        assertThat(requests.first().messages[1].content)
            .contains("Book: 운영체제", "Authors: 조영호 저", "Publisher: 위키북스, 2019", "Part 1 개관", "(3 items in all)")
        assertThat(
            requests
                .last()
                .messages
                .last()
                .content,
        ).contains("1.1 운영체제가 할 일")
        assertThat(ledger(reader).drop(4)).containsExactly(
            "reserve" to null,
            "settle" to "tool_calls",
            "reserve" to null,
            "settle" to "tool_calls",
            "reserve" to null,
            "settle" to "tool_calls",
        )
        val cost =
            jdbc
                .sql("select cost_micros from web_search where session_id = :session")
                .param("session", session.id)
                .query(Long::class.java)
                .single()
        assertThat(cost).isEqualTo(FakeWebSearchGateway.COST_MICROS)
    }

    @Test
    fun `the page's address, title, time and a hash of what was checked are kept, never its text`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, fetch, submit())

        pick(reader, session)

        val row =
            jdbc
                .sql(
                    """
                    select url, title, fetched_at, text_sha256, characters
                    from source_page where session_id = :session
                    """.trimIndent(),
                ).param("session", session.id)
                .query { rs, _ ->
                    listOf(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getString(4),
                        rs.getInt(5),
                    )
                }.single()
        val sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PAGE.toByteArray()))
        assertThat(row).containsExactly(NOTES, "운영체제 10판 목차 정리", clock.instant(), sha, PAGE.length)
        assertThat(kept(session.id)).doesNotContain(PAGE_ONLY)
        val sessionColumns =
            jdbc
                .sql("select coalesce(contents, '') from setup_session where id = :id")
                .param("id", session.id)
                .query(String::class.java)
                .single()
        assertThat(sessionColumns).doesNotContain(PAGE_ONLY)
    }

    @Test
    fun `a submission the page does not bear out is refused with why, and one written again is taken`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, fetch, submit(DETAILED + "    3.9 지어낸 절"), submit())

        val found = pick(reader, session)

        val refused =
            found.messages
                .map { it.payload }
                .filterIsInstance<ToolRefused>()
                .single()
        assertThat(refused.problem).contains("\"지어낸 절\"")
        assertThat(
            fakeLlmGateway.requests
                .last()
                .messages
                .last()
                .content,
        ).contains("지어낸 절")
        assertThat(found.held?.raw).isEqualTo(DETAILED.joinToString("\n"))
    }

    @Test
    fun `contents missing the bookshop's parts, or no more detailed, are refused as another edition's`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        val otherEdition = DETAILED.filterNot { it.startsWith("Part 2") }
        script(search, fetch, submit(otherEdition), submit(listOf("Part 1 개관", "Part 2 프로세스 관리")), says("못 찾았어요."))

        val notFound = pick(reader, session)

        val problems =
            notFound.messages
                .map { it.payload }
                .filterIsInstance<ToolRefused>()
                .map { it.problem }
        assertThat(problems[0]).contains("\"프로세스 관리\"", "another edition")
        assertThat(problems[1]).contains("no more detailed")
        assertThat(notFound.held?.raw).isEqualTo(PARTS)
    }

    @Test
    fun `only a page the search returned is read, and one the guard refuses is refused to the model`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        val inward = "https://inside.example/toc"
        fakeWebSearchGateway.pages = listOf(page(NOTES, "목차"), page(inward, "내부"))
        fakePageGateway.failing[inward] = PageFetchFailed.Kind.NOT_ALLOWED
        script(
            search,
            calls("fetch_page" to mapOf("url" to "http://169.254.169.254/latest/meta-data")),
            calls("fetch_page" to mapOf("url" to inward)),
            says("못 찾았어요."),
        )

        val notFound = pick(reader, session)

        val problems =
            notFound.messages
                .map { it.payload }
                .filterIsInstance<ToolRefused>()
                .map { it.problem }
        assertThat(problems).containsExactly(
            "fetch_page reads only a URL search_web returned in this turn",
            "only a public https page may be read",
        )
        assertThat(fakePageGateway.asked).containsExactly(inward)
    }

    @Test
    fun `nothing found is said in a sentence, and the bookshop's contents stay held`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, says("목차를 실은 페이지를 찾지 못했어요."))

        val notFound = pick(reader, session)

        assertThat(notFound.messages.last().payload).isEqualTo(
            SearchFailed(SetupError.CONTENTS_NOT_FOUND.code, "다른 출처에서 목차를 찾지 못했어요."),
        )
        val held = checkNotNull(notFound.held)
        assertThat(held.raw).isEqualTo(PARTS)
        assertThat(held.found).isNull()
        assertThat(notFound.searching).isFalse()
        val pages =
            jdbc
                .sql("select count(*) from source_page where session_id = :session")
                .param("session", session.id)
                .query(Int::class.java)
                .single()
        assertThat(pages).isZero()
    }

    @Test
    fun `the sixth call must submit what was read, and is not made when nothing was`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, fetch, fetch, fetch, fetch, submit(listOf("1장 없는 장", "2장 없는 장", "3장 없는 장", "4장 없는 장")))

        val forced = pick(reader, session)

        val requests = fakeLlmGateway.requests.drop(2)
        assertThat(requests).hasSize(6)
        assertThat(requests.dropLast(1).map { it.toolChoice }).containsOnly(ToolChoice.Auto)
        assertThat(requests.last().toolChoice).isEqualTo(ToolChoice.Named("submit_toc"))
        assertThat(fakePageGateway.asked).hasSize(3)
        assertThat((forced.messages.last().payload as SearchFailed).code).isEqualTo(SetupError.CONTENTS_NOT_FOUND.code)
        assertThat(ledger(reader).count { it.first == "settle" }).isEqualTo(8)

        val again = keyedReaders.create()
        val unread = shown(again)
        script(search, search, search, search, search)
        pick(again, unread)

        assertThat(fakeLlmGateway.requests.drop(10)).hasSize(5)
    }

    @Test
    fun `a call that fails ends its reservation, and the reader is told what went wrong`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script(search, {
            throw LlmCallFailed(LlmCallFailed.Kind.PROVIDER_ERROR, outputStarted = false, promptMayHaveBilled = false)
        })

        val failed = pick(reader, session)

        assertThat(failed.messages.last().payload)
            .isEqualTo(SearchFailed(LlmError.PROVIDER_FAILED.code, LlmError.PROVIDER_FAILED.message))
        assertThat(ledger(reader).drop(4)).containsExactly(
            "reserve" to null,
            "settle" to "tool_calls",
            "reserve" to null,
            "release" to "error",
        )
        assertThat(failed.held?.raw).isEqualTo(PARTS)
    }

    @Test
    fun `a call whose reservation would pass the reader's limit is never made`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        // Two settled calls spent $0.00072; 8,000 tokens of output alone would reserve $0.0096.
        budgetService.setLimit(reader, LimitRequest(5_000))
        script(search)

        val refused = pick(reader, session)

        assertThat((refused.messages.last().payload as SearchFailed).code).isEqualTo(BudgetError.LIMIT_REACHED.code)
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(ledger(reader).count { it.first == "reserve" }).isEqualTo(2)
    }

    @Test
    fun `a look past its deadline finds nothing, and its last call is settled`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        script({ request ->
            clock.advance(Duration.ofMinutes(4))
            search(request)
        })

        val late = pick(reader, session)

        assertThat((late.messages.last().payload as SearchFailed).code).isEqualTo(SetupError.CONTENTS_NOT_FOUND.code)
        assertThat(fakeWebSearchGateway.asked).isEmpty()
        assertThat(ledger(reader).drop(4)).containsExactly("reserve" to null, "settle" to "tool_calls")
    }

    @Test
    fun `a page read past the deadline ends the look before another call`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        fakePageGateway.whenFetched = { clock.advance(Duration.ofMinutes(4)) }
        script(search, fetch, submit())

        val late = pick(reader, session)

        assertThat((late.messages.last().payload as SearchFailed).code).isEqualTo(SetupError.CONTENTS_NOT_FOUND.code)
        assertThat(fakeLlmGateway.requests.drop(2)).hasSize(2)
        assertThat(late.held?.raw).isEqualTo(PARTS)
    }

    @Test
    fun `while contents are looked for, the held ones are not started with`() {
        val reader = keyedReaders.create()
        val session = shown(reader)
        var refused: Throwable? = null
        script({ request ->
            val now = setupSessionService.get(reader, session.id)
            refused =
                runCatching {
                    setupSessionService.confirm(
                        reader,
                        session.id,
                        SetupConfirmRequest(revision = now.revision, level = "work", voice = "explain"),
                    )
                }.exceptionOrNull()
            says("못 찾았어요.")(request)
        })

        pick(reader, session)

        assertThat((refused as GlossException).error).isEqualTo(SetupError.STILL_SEARCHING)
    }

    @Test
    fun `detailed contents are looked for only when the reader asks, and a coarse edition only once`() {
        val reader = keyedReaders.create()
        val deep = shown(reader, contents = DEEP)
        val picked = pick(reader, deep)
        assertThat(fakeLlmGateway.requests).hasSize(2)
        assertThat(kinds(picked).last()).isEqualTo("contents")
        script(search, fetch, submit())

        val sought = setupMessageService.seek(reader, deep.id, SetupSeekRequest(picked.revision))

        assertThat(sought.messages.first { it.kind == "seek" }.payload).isEqualTo(ReaderSought("101"))
        assertThat(sought.held?.found?.url).isEqualTo(NOTES)
        assertThat(sought.held?.raw).isEqualTo(DETAILED.joinToString("\n"))

        val coarse = keyedReaders.create()
        val first = shown(coarse)
        script(search, says("못 찾았어요."))
        val notFound = pick(coarse, first)
        val again = setupMessageService.pick(coarse, first.id, SetupPickRequest("101", notFound.revision))
        assertThat(kinds(again).takeLast(2)).containsExactly("pick", "contents")
        assertThat(again.searching).isFalse()
    }

    @Test
    fun `another source is not looked for with no web search, or with nothing picked yet`() {
        val reader = keyedReaders.create()
        val session = shown(reader, contents = DEEP)
        val picked = pick(reader, session)
        fakeWebSearchGateway.available = false

        assertThatThrownBy { setupMessageService.seek(reader, session.id, SetupSeekRequest(picked.revision)) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(SetupError.CONTENTS_SEARCH_UNAVAILABLE)
            }

        fakeWebSearchGateway.available = true
        val other = keyedReaders.create()
        script(says("어떤 책을 찾으세요?"))
        val searching = setupMessageService.start(other, SetupMessageRequest("책"))
        assertThatThrownBy { setupMessageService.seek(other, searching.id, SetupSeekRequest(searching.revision)) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(SetupError.NO_BOOK_YET)
            }
    }

    private companion object {
        const val NOTES = "https://notes.example/os-toc"
        const val PARTS = "Part 1 개관\nPart 2 프로세스 관리\nPart 3 메모리 관리"

        // Under the coarse look's rule: a chapter under a part.
        const val DEEP = "Part 1 개관\n  1장 서론\nPart 2 프로세스 관리\nPart 3 메모리 관리"
        const val PAGE_ONLY = "이 글은 공부하며 정리한 목차입니다"

        val DETAILED =
            listOf(
                "Part 1 개관",
                "  1장 서론",
                "    1.1 운영체제가 할 일",
                "    1.2 컴퓨터 시스템의 구성",
                "Part 2 프로세스 관리",
                "  3장 프로세스",
                "    3.1 프로세스 개념",
                "Part 3 메모리 관리",
                "  9장 메인 메모리",
                "    9.1 배경",
            )

        val PAGE =
            """
            $PAGE_ONLY
            PART 1. 개관
            Chapter 1 서론
            1.1 운영체제가 할 일
            1.2 컴퓨터 시스템의 구성
            PART 2. 프로세스 관리
            Chapter 3 프로세스
            3.1 프로세스 개념
            PART 3. 메모리 관리
            Chapter 9 메인 메모리
            9.1 배경
            """.trimIndent()

        val USAGE =
            TokenUsage(cacheHitTokens = 0, cacheMissTokens = 1_000, completionTokens = 50, reasoningTokens = null)
    }
}
