package gloss.domain.setup.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.budget.service.BudgetError
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
import gloss.domain.llm.service.DeepSeekModel
import gloss.domain.llm.service.LlmError
import gloss.domain.setup.FakeWebSearchGateway
import gloss.domain.setup.SetupFakes
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.BooksShown
import gloss.domain.setup.domain.CitedPage
import gloss.domain.setup.domain.GlossSaid
import gloss.domain.setup.domain.Recommended
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.WebPage
import gloss.domain.setup.domain.WebSearched
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.response.SetupSessionResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Duration
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
    KeyedReaders.Config::class,
    MutableClock.Config::class,
    SetupFakes.Config::class,
)
class SetupTurnServiceTest(
    @Autowired private val setupMessageService: SetupMessageService,
    @Autowired private val setupFakes: SetupFakes,
    @Autowired private val keyedReaders: KeyedReaders,
    @Autowired private val jdbc: JdbcClient,
) {
    private val json = JsonMapper()
    private val fakeLlmGateway = setupFakes.model
    private val fakeBookSourceGateway = setupFakes.books
    private val fakeWebSearchGateway = setupFakes.web
    private val clock = setupFakes.clock

    @BeforeEach
    fun reset() {
        setupFakes.reset()
    }

    /** The model's replies, one a call, in order; a call past them fails the test. */
    private fun script(vararg replies: (ChatRequest) -> ChatResult) {
        val left = ArrayDeque(replies.toList())
        fakeLlmGateway.answer = { _, _ -> left.removeFirst()(fakeLlmGateway.requests.last()) }
    }

    private fun calls(vararg calls: Pair<String, Any>): (ChatRequest) -> ChatResult =
        { request ->
            val made =
                calls.mapIndexed { i, (name, arguments) ->
                    val written = arguments as? String ?: json.writeValueAsString(arguments)
                    ToolCall(id = "call-${request.messages.size}-$i", name = name, arguments = written)
                }
            ChatResult(text = "", finishReason = FinishReason.TOOL_CALLS, usage = USAGE, toolCalls = made)
        }

    private fun says(text: String): (ChatRequest) -> ChatResult =
        { ChatResult(text = text, finishReason = FinishReason.STOP, usage = USAGE) }

    private fun start(
        account: UUID,
        text: String,
    ) = setupMessageService.start(account, SetupMessageRequest(text))

    private fun say(
        session: SetupSessionResponse,
        account: UUID,
        text: String,
    ) = setupMessageService.say(account, session.id, SetupMessageRequest(text, revision = session.revision))

    private fun gloss(session: SetupSessionResponse) = session.messages.filter { it.role == "gloss" }.map { it.payload }

    private fun kinds(session: SetupSessionResponse) = session.messages.map { it.kind }

    /** The ledger's rows for the reader: what each reservation was for and how it ended. */
    private fun ledger(account: UUID): List<Triple<String, String, String?>> =
        jdbc
            .sql("select kind, purpose, outcome from usage_ledger where account_id = :account order by id")
            .param("account", account)
            .query { row, _ -> Triple(row.getString("kind"), row.getString("purpose"), row.getString("outcome")) }
            .list()

    private fun kept(sessionId: UUID): String =
        jdbc
            .sql("select string_agg(payload::text, ' ') from setup_message where session_id = :session")
            .param("session", sessionId)
            .query(String::class.java)
            .single()

    @Test
    fun `a named book is looked up and its editions shown, every call reserved and settled on the reader's key`() {
        val reader = keyedReaders.create()
        fakeWebSearchGateway.available = false
        fakeBookSourceGateway.holds("101")
        script(calls("find_books" to mapOf("query" to "오브젝트")), calls("show_books" to mapOf("query" to "오브젝트")))

        val session = start(reader, "오브젝트")

        assertThat(kinds(session)).containsExactly("text", "tool_calls", "books", "tool_calls", "books_shown")
        val found = gloss(session)[1] as BooksFound
        assertThat(found.callId).isEqualTo("call-2-0")
        assertThat(found.books.map { it.ref }).containsExactly("101")
        assertThat(gloss(session)[3]).isEqualTo(BooksShown(callId = "call-4-0", booksCallId = "call-2-0"))
        assertThat(session.searching).isFalse()
        val requests = fakeLlmGateway.requests
        assertThat(requests).hasSize(2)
        assertThat(requests).allSatisfy {
            assertThat(it.model).isEqualTo(DeepSeekModel.ID)
            assertThat(it.thinking).isEqualTo(Thinking.DISABLED)
            assertThat(it.maxTokens).isEqualTo(2_000)
            assertThat(it.toolChoice).isEqualTo(ToolChoice.Auto)
            assertThat(it.tools.map { tool -> tool.name }).containsExactly("find_books", "recommend", "show_books")
            assertThat(it.messages.first().content).isEqualTo(SetupPrompt.SYSTEM)
        }
        // Within the turn the model reads the source's details; the session keeps only the refs.
        assertThat(requests[1].messages.last().content).contains("위키북스")
        assertThat(kept(session.id)).doesNotContain("위키북스", "조영호", "yes24.com")
        assertThat(ledger(reader)).containsExactly(
            Triple("reserve", "setup", null),
            Triple("settle", "setup", "tool_calls"),
            Triple("reserve", "setup", null),
            Triple("settle", "setup", "tool_calls"),
        )
    }

    @Test
    fun `a subject is searched on the web and its books looked up, and the recommendation keeps only what was found`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "오브젝트 원서")
        val page = "https://example.com/oop-books"
        fakeWebSearchGateway.pages =
            listOf(WebPage(url = page, title = "객체지향 책 비교", published = "2026-01-02", highlights = listOf("첫 책으로 좋다")))
        val books =
            listOf(
                mapOf("ref" to "102", "reason" to "원서로 읽어요.", "sources" to listOf(page, "https://made.up/page")),
                mapOf("ref" to "101", "reason" to "번역서예요."),
                mapOf("ref" to "999", "reason" to "찾지 않은 책이에요."),
            )
        script(
            calls("search_web" to mapOf("query" to "객체지향 입문 책 추천")),
            calls("find_books" to mapOf("query" to "오브젝트")),
            calls("recommend" to mapOf("intro" to "두 권을 골랐어요.", "books" to books)),
        )

        val session = start(reader, "객체지향 공부할 책 추천해줘")

        assertThat(kinds(session)).containsExactly(
            "text",
            "tool_calls",
            "web_searched",
            "tool_calls",
            "books",
            "tool_calls",
            "recommend",
        )
        assertThat(
            fakeLlmGateway.requests
                .first()
                .tools
                .map { it.name },
        ).containsExactly("find_books", "search_web", "recommend", "show_books")
        assertThat(fakeWebSearchGateway.asked).containsExactly("객체지향 입문 책 추천")
        assertThat(gloss(session)[1]).isEqualTo(WebSearched(callId = "call-2-0", query = "객체지향 입문 책 추천", results = 1))
        val recommended = gloss(session).last() as Recommended
        assertThat(recommended.intro).isEqualTo("두 권을 골랐어요.")
        assertThat(recommended.books.map { it.ref }).containsExactly("102", "101")
        assertThat(recommended.books.first().sources).containsExactly(CitedPage(url = page, title = "객체지향 책 비교"))
        assertThat(recommended.books.last().sources).isEmpty()
        // The page is read only within the turn: its excerpts are never kept.
        assertThat(
            fakeLlmGateway.requests[1]
                .messages
                .last()
                .content,
        ).contains("첫 책으로 좋다")
        assertThat(kept(session.id)).doesNotContain("첫 책으로 좋다", "위키북스")
        val search =
            jdbc
                .sql("select outcome, results, cost_micros, request_id from web_search where session_id = :session")
                .param("session", session.id)
                .query { row, _ -> listOf(row.getString(1), row.getInt(2), row.getLong(3), row.getString(4)) }
                .single()
        assertThat(search).containsExactly("answered", 1, FakeWebSearchGateway.COST_MICROS, "exa-request")
        assertThat(ledger(reader).count { it.first == "reserve" }).isEqualTo(3)
        assertThat(ledger(reader).count { it.first == "settle" }).isEqualTo(3)
    }

    @Test
    fun `a recommendation of books no search returned is refused, and the model may write it again`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "오브젝트 원서")
        val invented = listOf(mapOf("ref" to "998", "reason" to "가짜예요."), mapOf("ref" to "999", "reason" to "가짜예요."))
        val real = listOf(mapOf("ref" to "101", "reason" to "번역서예요."), mapOf("ref" to "102", "reason" to "원서예요."))
        script(
            calls("find_books" to mapOf("query" to "오브젝트")),
            calls("recommend" to mapOf("intro" to "골랐어요.", "books" to invented)),
            calls("recommend" to mapOf("intro" to "골랐어요.", "books" to real)),
        )

        val session = start(reader, "객체지향 책 추천")

        val refused = gloss(session).filterIsInstance<ToolRefused>().single()
        assertThat(refused.problem).contains("998", "999")
        assertThat(
            fakeLlmGateway.requests[2]
                .messages
                .last()
                .content,
        ).contains("998")
        assertThat((gloss(session).last() as Recommended).books.map { it.ref }).containsExactly("101", "102")
    }

    @Test
    fun `a recommendation asked with a search is refused, and only one asked alone ends the turn`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "오브젝트 원서")
        val books = listOf(mapOf("ref" to "101", "reason" to "번역서예요."), mapOf("ref" to "102", "reason" to "원서예요."))
        val recommendation = "recommend" to mapOf("intro" to "골랐어요.", "books" to books)
        script(calls("find_books" to mapOf("query" to "오브젝트"), recommendation), calls(recommendation))

        val session = start(reader, "객체지향 책 추천")

        assertThat(kinds(session))
            .containsExactly("text", "tool_calls", "books", "tool_refused", "tool_calls", "recommend")
        assertThat((gloss(session)[2] as ToolRefused).problem).contains("alone")
        assertThat(fakeLlmGateway.requests).hasSize(2)
    }

    @Test
    fun `an answer cut at its cap or filtered never completes the turn, however well formed`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "오브젝트 원서")
        val books = listOf(mapOf("ref" to "101", "reason" to "번역서예요."), mapOf("ref" to "102", "reason" to "원서예요."))
        val cut: (ChatRequest) -> ChatResult = { request ->
            calls("recommend" to mapOf("intro" to "골랐어요.", "books" to books))(request)
                .copy(finishReason = FinishReason.LENGTH)
        }
        script(calls("find_books" to mapOf("query" to "오브젝트")), cut)

        val lengthy = start(reader, "객체지향 책 추천")
        script({ ChatResult(text = "이 책을 권해요.", finishReason = FinishReason.CONTENT_FILTER, usage = USAGE) })
        val filtered = say(lengthy, reader, "다시 추천해 줘")

        val noAnswer = SearchFailed(SetupError.NO_ANSWER.code, SetupError.NO_ANSWER.message)
        assertThat(gloss(lengthy).last()).isEqualTo(noAnswer)
        assertThat(kinds(lengthy)).doesNotContain("recommend")
        assertThat(gloss(filtered).last()).isEqualTo(noAnswer)
        assertThat(kinds(filtered)).doesNotContain("said")
    }

    @Test
    fun `arguments that are not JSON and a tool not offered are refused to the model, which answers in words`() {
        val reader = keyedReaders.create()
        fakeWebSearchGateway.available = false
        script(
            calls("find_books" to "{\"query\": \"오브젝트\"", "search_web" to mapOf("query" to "객체지향")),
            says("어떤 책을 찾으세요?"),
        )

        val session = start(reader, "책")

        assertThat(kinds(session)).containsExactly("text", "tool_calls", "tool_refused", "tool_refused", "said")
        assertThat(gloss(session).last()).isEqualTo(GlossSaid("어떤 책을 찾으세요?"))
        assertThat(fakeWebSearchGateway.asked).isEmpty()
        assertThat(fakeBookSourceGateway.asked).isEmpty()
        // Each result follows its call, in the calls' order.
        val second = fakeLlmGateway.requests[1].messages
        assertThat(second.takeLast(3).map { it.role }).containsExactly(Role.ASSISTANT, Role.TOOL, Role.TOOL)
        assertThat(second.takeLast(2).map { it.toolCallId }).containsExactly("call-2-0", "call-2-1")
    }

    @Test
    fun `a turn stops at its caps, and its last call may only answer in words`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        val many = (1..7).map { "find_books" to mapOf("query" to "오브젝트 $it") }.toTypedArray()
        script(calls(*many), *Array(5) { calls("find_books" to mapOf("query" to "오브젝트")) })

        val session = start(reader, "오브젝트")

        val requests = fakeLlmGateway.requests
        assertThat(requests).hasSize(6)
        assertThat(requests.dropLast(1)).allSatisfy { assertThat(it.toolChoice).isEqualTo(ToolChoice.Auto) }
        assertThat(requests.last().toolChoice).isEqualTo(ToolChoice.None)
        assertThat(fakeBookSourceGateway.asked).hasSize(6)
        assertThat(gloss(session).filterIsInstance<ToolRefused>().map { it.problem }.distinct())
            .containsExactly("no more find_books calls in this turn")
        assertThat(
            gloss(session).last(),
        ).isEqualTo(SearchFailed(SetupError.NO_ANSWER.code, SetupError.NO_ANSWER.message))
        assertThat(session.searching).isFalse()
        assertThat(ledger(reader).count { it.first == "reserve" }).isEqualTo(6)
        assertThat(ledger(reader).count { it.first == "settle" }).isEqualTo(6)
    }

    @Test
    fun `a call that fails ends its reservation, and the reader is told what went wrong`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        script(
            calls("find_books" to mapOf("query" to "오브젝트")),
            {
                throw LlmCallFailed(
                    LlmCallFailed.Kind.PROVIDER_ERROR,
                    outputStarted = false,
                    promptMayHaveBilled = false,
                )
            },
        )

        val session = start(reader, "오브젝트")

        assertThat(gloss(session).last())
            .isEqualTo(SearchFailed(LlmError.PROVIDER_FAILED.code, LlmError.PROVIDER_FAILED.message))
        assertThat(ledger(reader).map { it.first to it.third }).containsExactly(
            "reserve" to null,
            "settle" to "tool_calls",
            "reserve" to null,
            "release" to "error",
        )
        assertThat(session.searching).isFalse()

        script(says("네, 찾아볼게요."))
        say(session, reader, "다시 찾아 줘")

        // The failed turn is never read again: only the message being answered.
        val replayed = fakeLlmGateway.requests.last().messages
        assertThat(replayed.map { it.role }).containsExactly(Role.SYSTEM, Role.USER)
        assertThat(replayed.last().content).isEqualTo("다시 찾아 줘")
    }

    @Test
    fun `a turn past its deadline asks no more, and its last call is settled`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        script({ request ->
            clock.advance(Duration.ofMinutes(4))
            calls("find_books" to mapOf("query" to "오브젝트"))(request)
        })

        val session = start(reader, "오브젝트")

        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(gloss(session).last())
            .isEqualTo(SearchFailed(SetupError.TURN_TIMED_OUT.code, SetupError.TURN_TIMED_OUT.message))
        assertThat(ledger(reader).map { it.first }).containsExactly("reserve", "settle")
    }

    @Test
    fun `a turn whose deadline passes during its tools runs none of the rest`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.whenAsked = { clock.advance(Duration.ofMinutes(4)) }
        script(calls(*(1..3).map { "find_books" to mapOf("query" to "오브젝트 $it") }.toTypedArray()))

        val session = start(reader, "오브젝트")

        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트 1")
        assertThat(kinds(session)).containsExactly("text", "search_failed")
        assertThat(gloss(session).last())
            .isEqualTo(SearchFailed(SetupError.TURN_TIMED_OUT.code, SetupError.TURN_TIMED_OUT.message))
        assertThat(ledger(reader).map { it.first }).containsExactly("reserve", "settle")
    }

    @Test
    fun `without a key or a limit the reader is told where to set it, and nothing is reserved`() {
        val keyless = keyedReaders.create(key = false)
        val unlimited = keyedReaders.create(limitMicros = null)

        val noKey = start(keyless, "오브젝트")
        val noLimit = start(unlimited, "오브젝트")

        assertThat(gloss(noKey)).containsExactly(SearchFailed(LlmError.NO_KEY.code, LlmError.NO_KEY.message))
        assertThat(gloss(noLimit))
            .containsExactly(SearchFailed(BudgetError.LIMIT_NOT_SET.code, BudgetError.LIMIT_NOT_SET.message))
        assertThat(fakeLlmGateway.requests).isEmpty()
        assertThat(ledger(keyless) + ledger(unlimited)).isEmpty()
    }

    @Test
    fun `web searches past a reader's daily cap are refused without asking the engine`() {
        val reader = keyedReaders.create()
        script(says("무엇을 공부하고 싶으세요?"))
        val session = start(reader, "안녕")
        repeat(20) {
            jdbc
                .sql(
                    """
                    insert into web_search (id, account_id, session_id, asked_at)
                    values (:id, :account, :session, :at)
                    """.trimIndent(),
                ).param("id", UUID.randomUUID())
                .param("account", reader)
                .param("session", session.id)
                .param("at", Timestamp.from(clock.instant().minus(Duration.ofHours(23))))
                .update()
        }
        script(calls("search_web" to mapOf("query" to "운영체제 교재")), says("YES24에서 찾아볼게요."))

        val again = say(session, reader, "운영체제 책 추천")

        assertThat(fakeWebSearchGateway.asked).isEmpty()
        assertThat(gloss(again).filterIsInstance<ToolRefused>().single().problem)
            .isEqualTo("web search is used up for today; use find_books")
    }

    @Test
    fun `a later message reads an earlier turn's searches as refs alone, and its start as it was sent`() {
        val reader = keyedReaders.create()
        fakeBookSourceGateway.holds("101")
        fakeWebSearchGateway.pages =
            listOf(WebPage(url = "https://example.com/a", title = "비교", published = null, highlights = listOf("발췌")))
        script(
            calls("search_web" to mapOf("query" to "객체지향 책")),
            calls("find_books" to mapOf("query" to "오브젝트")),
            says("오브젝트를 권해요."),
        )
        val first = start(reader, "객체지향 책")
        script(says("네, 좋아요."))

        say(first, reader, "고마워요")

        val firstCall = fakeLlmGateway.requests.first().messages
        val later = fakeLlmGateway.requests.last().messages
        assertThat(later.take(firstCall.size)).isEqualTo(firstCall)
        val replayed = later.filter { it.role == Role.TOOL }.map { it.content }
        assertThat(replayed).hasSize(2)
        assertThat(replayed[0]).contains("\"results\":1").doesNotContain("발췌", "example.com")
        assertThat(replayed[1]).contains("\"ref\":\"101\"").doesNotContain("오브젝트", "위키북스")
        assertThat(later.last().content).isEqualTo("고마워요")
        assertThat(later[later.size - 2].content).isEqualTo("오브젝트를 권해요.")
    }

    private companion object {
        val USAGE =
            TokenUsage(cacheHitTokens = 0, cacheMissTokens = 1_000, completionTokens = 50, reasoningTokens = null)
    }
}
