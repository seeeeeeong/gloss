package gloss.domain.chat.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.chat.TutorBooks
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.chat.dto.request.ChatStopRequest
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

// Readers and books made here stay: their messages and the ledger are append-only.
@SpringBootTest
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeLlmGateway.Config::class,
    KeyedReaders.Config::class,
    TutorBooks.Config::class,
)
class TurnServiceTest(
    @Autowired private val turnService: TurnService,
    @Autowired private val chatService: ChatService,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val readers: KeyedReaders,
    @Autowired private val books: TutorBooks,
    @Autowired private val jdbc: JdbcClient,
) {
    private val usage =
        TokenUsage(cacheHitTokens = 1_400, cacheMissTokens = 200, completionTokens = 300, reasoningTokens = 0)

    @BeforeEach
    fun reset() {
        fakeLlmGateway.reset()
    }

    /** What the reader was sent, in order, and whether they have left. */
    private class Reader {
        val events = mutableListOf<TurnEvent>()
        var gone = false
        val stream = TurnStream(send = { events += it }, isClosed = { gone })
    }

    private fun answers(
        vararg pieces: String,
        finish: FinishReason = FinishReason.STOP,
    ) {
        fakeLlmGateway.answer = { onText, _ ->
            pieces.forEach(onText)
            ChatResult(pieces.joinToString(""), finish, usage)
        }
    }

    /** Streams [piece], then waits on a cancel as the gateway does, and stops the way it stops. */
    private fun answersUntilCancelled(
        piece: String,
        afterPiece: () -> Unit,
    ) {
        fakeLlmGateway.answer = { onText, isCancelled ->
            onText(piece)
            afterPiece()
            check(isCancelled()) { "the turn was not cancelled" }
            throw LlmCallFailed(LlmCallFailed.Kind.CANCELLED, outputStarted = true)
        }
    }

    // The conversation each book's turns go on, named by its first turn's message.
    private val sessions = mutableMapOf<UUID, UUID>()

    private fun ask(
        book: TutorBooks.Shelved,
        text: String = "자원 할당자가 뭔가요?",
        itemKey: String? = book.keys[1],
        quote: String? = null,
        reader: Reader = Reader(),
        turnId: UUID? = null,
    ): Reader {
        val request =
            ChatAskRequest(
                text = text,
                itemKey = itemKey,
                quote = quote,
                turnId = turnId,
                sessionId = sessions[book.id],
            )
        val stream =
            TurnStream(
                send = { event ->
                    if (event is TurnEvent.Asked) sessions.putIfAbsent(book.id, event.message.sessionId)
                    reader.stream.send(event)
                },
                isClosed = reader.stream.isClosed,
            )
        turnService.ask(accountId = book.account, bookId = book.id, request = request, stream = stream)
        return reader
    }

    private fun answered(reader: Reader) = (reader.events.last() as TurnEvent.Answered).message

    /** Each ended row of the ledger, as kind:outcome; one for every reservation. */
    private fun endings(account: UUID): List<String?> =
        jdbc
            .sql(
                """
                select kind || ':' || coalesce(outcome, '') from usage_ledger
                where account_id = :id and kind <> 'reserve' order by id
                """.trimIndent(),
            ).param("id", account)
            .query(String::class.java)
            .list()

    /** What the reader's conversations hold, in the order it was said. */
    private fun said(account: UUID): List<String?> =
        jdbc
            .sql("select text from chat_message where account_id = :id order by created_at, seq")
            .param("id", account)
            .query(String::class.java)
            .list()

    private fun lastStatus(account: UUID): String? =
        jdbc
            .sql("select status from chat_message where account_id = :id order by created_at desc, seq desc limit 1")
            .param("id", account)
            .query(String::class.java)
            .single()

    private fun reserves(account: UUID): Int =
        jdbc
            .sql("select count(*) from usage_ledger where account_id = :id and kind = 'reserve'")
            .param("id", account)
            .query(Int::class.java)
            .single()

    @Test
    fun `a turn sends the reader's message, the answer as it comes, then the reply as kept`() {
        val book = books.create(readers.create())
        answers("자원을 ", "나눠 주는 역할이에요.")

        val reader = ask(book)

        assertThat(reader.events.map { it::class.simpleName }).containsExactly("Asked", "Text", "Text", "Answered")
        val asked = (reader.events.first() as TurnEvent.Asked).message
        assertThat(asked.role).isEqualTo("reader")
        assertThat(asked.text).isEqualTo("자원 할당자가 뭔가요?")
        assertThat(asked.itemKey).isEqualTo(book.keys[1])
        assertThat(
            reader.events.filterIsInstance<TurnEvent.Text>().map { it.text },
        ).containsExactly("자원을 ", "나눠 주는 역할이에요.")
        val reply = answered(reader)
        assertThat(reply.role).isEqualTo("gloss")
        assertThat(reply.status).isEqualTo("done")
        assertThat(reply.text).isEqualTo("자원을 나눠 주는 역할이에요.")
        assertThat(reply.seq).isEqualTo(asked.seq + 1)
        assertThat(reply.replyTo).isEqualTo(asked.seq)
        assertThat(asked.replyTo).isNull()
        assertThat(said(book.account))
            .containsExactly("자원 할당자가 뭔가요?", "자원을 나눠 주는 역할이에요.")
        assertThat(endings(book.account)).containsExactly("settle:stop")
        val call = fakeLlmGateway.requests.single()
        assertThat(call.maxTokens).isEqualTo(2_000)
        assertThat(call.thinking).isEqualTo(Thinking.DISABLED)
        assertThat(call.model).isEqualTo("deepseek-flash")
    }

    @Test
    fun `a reply keeps how much of the prompt the cache held, and the reservation it was settled under`() {
        val book = books.create(readers.create())
        answers("네.")

        ask(book)

        val row =
            jdbc
                .sql(
                    """
                    select cache_hit_tokens || ':' || cache_miss_tokens || ':' || completion_tokens || ':' ||
                           model || ':' || prompt_version || ':' || (reservation_id is not null)
                    from chat_message where account_id = :id and role = 'gloss'
                    """.trimIndent(),
                ).param("id", book.account)
                .query(String::class.java)
                .single()
        assertThat(row).isEqualTo("1400:200:300:deepseek-flash:tutor-1:true")
    }

    @Test
    fun `every turn is sent the turns before it byte for byte, so the cache holds all but the new one`() {
        val book = books.create(readers.create())
        books.write(book, book.keys[1], "## 학습 목표\n\n티켓 판매 글이다.")
        books.write(book, book.keys[2], "## 학습 목표\n\n설계 글이다.")
        answers("첫 답이에요.")
        ask(book, text = "첫 질문")
        answers("둘째 답이에요.")
        ask(book, text = "둘째 질문")
        answers("셋째 답이에요.")
        ask(book, text = "셋째 질문", itemKey = book.keys[2])

        val (first, second, third) = fakeLlmGateway.requests.map { it.messages }
        assertThat(second.take(first.size)).isEqualTo(first)
        assertThat(second[first.size]).isEqualTo(ChatMessage(Role.ASSISTANT, "첫 답이에요."))
        assertThat(third.take(second.size)).isEqualTo(second)
        assertThat(third[second.size]).isEqualTo(ChatMessage(Role.ASSISTANT, "둘째 답이에요."))
        assertThat(first.first().role).isEqualTo(Role.SYSTEM)
        assertThat(first.first().content).endsWith(TutorBooks.PLAN)
        // An item's post goes in once, with the first turn asked from it.
        assertThat(first.last().content).contains("티켓 판매 글이다.")
        assertThat(second.last().content).doesNotContain("티켓 판매 글이다.").endsWith("질문: 둘째 질문")
        assertThat(third.last().content).contains("설계 글이다.").doesNotContain("티켓 판매 글이다.")
    }

    @Test
    fun `a reader who stops keeps what was shown, the reservation is ended, and the turn is not sent again`() {
        val book = books.create(readers.create())
        val turnId = UUID.randomUUID()
        answersUntilCancelled("자원을 ") { chatService.stop(book.account, ChatStopRequest(turnId)) }

        val stopped = answered(ask(book, text = "멈출 질문", turnId = turnId))
        answers("다음 답이에요.")
        ask(book, text = "다음 질문")

        assertThat(stopped.status).isEqualTo("stopped")
        assertThat(stopped.text).isEqualTo("자원을 ")
        assertThat(endings(book.account)).containsExactly("settle:cancelled_after_output", "settle:stop")
        assertThat(reserves(book.account)).isEqualTo(2)
        val next = fakeLlmGateway.requests.last().messages
        assertThat(next).hasSize(2)
        assertThat(next.last().content).endsWith("질문: 다음 질문")
    }

    @Test
    fun `a reader who leaves stops the call, and the reply is kept for when they come back`() {
        val book = books.create(readers.create())
        val reader = Reader()
        answersUntilCancelled("자원을 ") { reader.gone = true }

        ask(book, reader = reader)

        assertThat(said(book.account).last()).isEqualTo("자원을 ")
        assertThat(lastStatus(book.account)).isEqualTo("stopped")
        assertThat(endings(book.account)).containsExactly("settle:cancelled_after_output")
    }

    @Test
    fun `a stop before the call goes out reserves nothing`() {
        val book = books.create(readers.create())
        val reader = Reader().apply { gone = true }

        val reply = answered(ask(book, reader = reader))

        assertThat(reply.status).isEqualTo("stopped")
        assertThat(reply.text).isEmpty()
        assertThat(reserves(book.account)).isZero()
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `a stop that comes before its turn stops it as it starts, and nothing is reserved`() {
        val book = books.create(readers.create())
        val turnId = UUID.randomUUID()
        chatService.stop(book.account, ChatStopRequest(turnId))

        val reply = answered(ask(book, turnId = turnId))
        answers("다음 답이에요.")
        val next = answered(ask(book, text = "다음 질문"))

        assertThat(reply.status).isEqualTo("stopped")
        assertThat(fakeLlmGateway.requests).hasSize(1)
        assertThat(next.status).isEqualTo("done")
        assertThat(reserves(book.account)).isEqualTo(1)
    }

    @Test
    fun `another reader's stop does not reach the turn`() {
        val book = books.create(readers.create())
        val turnId = UUID.randomUUID()
        chatService.stop(readers.create(), ChatStopRequest(turnId))
        answers("답이에요.")

        assertThat(answered(ask(book, turnId = turnId)).status).isEqualTo("done")
    }

    @Test
    fun `turns under a plan since replaced are not sent again`() {
        val book = books.create(readers.create())
        answers("옛 답이에요.")
        ask(book, text = "옛 질문")
        books.replan(book)
        answers("새 답이에요.")
        ask(book, text = "새 질문")
        answers("다음 답이에요.")
        ask(book, text = "다음 질문")

        val (_, underNew, next) = fakeLlmGateway.requests.map { it.messages }
        assertThat(underNew).hasSize(2)
        assertThat(underNew.first().content).endsWith(TutorBooks.REPLANNED)
        assertThat(next).hasSize(4)
        assertThat(next[1].content).endsWith("질문: 새 질문")
        assertThat(next[2].content).isEqualTo("새 답이에요.")
    }

    @Test
    fun `an answer that finished with nothing in it is a failure, never sent back`() {
        val book = books.create(readers.create())
        answers("  ")

        val empty = answered(ask(book, text = "빈 답 질문"))
        answers("다음 답이에요.")
        ask(book, text = "다음 질문")

        assertThat(empty.status).isEqualTo("failed")
        assertThat(empty.code).isEqualTo("chat.empty_answer")
        assertThat(empty.message).isEqualTo("답이 비어 있었어요. 다시 물어 주세요.")
        assertThat(endings(book.account)).containsExactly("settle:stop", "settle:stop")
        assertThat(fakeLlmGateway.requests.last().messages).hasSize(2)
    }

    @Test
    fun `a turn asked without an id of its own stops by the one the server gave it`() {
        val book = books.create(readers.create())
        val reader = Reader()
        answersUntilCancelled("자원을 ") {
            val issued = checkNotNull((reader.events.first() as TurnEvent.Asked).message.turnId)
            chatService.stop(book.account, ChatStopRequest(issued))
        }

        assertThat(answered(ask(book, reader = reader)).status).isEqualTo("stopped")
    }

    @Test
    fun `an answer the model cut short is kept as cut and never sent back`() {
        val book = books.create(readers.create())
        answers("길게 이어지다", finish = FinishReason.LENGTH)

        val cut = answered(ask(book, text = "긴 질문"))
        answers("짧은 답이에요.")
        ask(book, text = "짧은 질문")

        assertThat(cut.status).isEqualTo("cut")
        assertThat(cut.code).isEqualTo("length")
        assertThat(cut.text).isEqualTo("길게 이어지다")
        assertThat(endings(book.account)).containsExactly("settle:length", "settle:stop")
        assertThat(fakeLlmGateway.requests.last().messages).hasSize(2)
    }

    @Test
    fun `a call the provider failed ends the turn as failed, in the reader's words, its reservation released`() {
        val book = books.create(readers.create())
        fakeLlmGateway.answer = { _, _ ->
            throw LlmCallFailed(LlmCallFailed.Kind.PROVIDER_ERROR, outputStarted = false, promptMayHaveBilled = false)
        }

        val failed = answered(ask(book))

        assertThat(failed.status).isEqualTo("failed")
        assertThat(failed.code).isEqualTo("llm.provider_failed")
        assertThat(failed.message).isEqualTo("DeepSeek에서 답을 받지 못했어요. 잠시 후 다시 시도해 주세요.")
        assertThat(endings(book.account)).containsExactly("release:error")
    }

    @Test
    fun `a reader without a key is told so, and nothing is reserved`() {
        val book = books.create(readers.create(key = false))

        val failed = answered(ask(book))

        assertThat(failed.status).isEqualTo("failed")
        assertThat(failed.code).isEqualTo("llm.no_key")
        assertThat(reserves(book.account)).isZero()
    }

    @Test
    fun `a reader at their limit is told so, and nothing is reserved`() {
        val book = books.create(readers.create(limitMicros = 1))

        val failed = answered(ask(book))

        assertThat(failed.code).isEqualTo("budget.limit_reached")
        assertThat(reserves(book.account)).isZero()
    }

    @Test
    fun `one turn of a conversation is answered at a time`() {
        val book = books.create(readers.create())
        var second: Throwable? = null
        fakeLlmGateway.answer = { _, _ ->
            second = runCatching { ask(book, text = "끼어든 질문") }.exceptionOrNull()
            ChatResult("답이에요.", FinishReason.STOP, usage)
        }

        ask(book)
        answers("다음 답이에요.")
        val after = answered(ask(book, text = "다음 질문"))

        assertThat(second).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(ChatError.ANSWERING)
        }
        assertThat(after.status).isEqualTo("done")
        assertThat(said(book.account))
            .containsExactly("자원 할당자가 뭔가요?", "답이에요.", "다음 질문", "다음 답이에요.")
    }

    @Test
    fun `a question is refused before anything is kept when the book cannot be asked about`() {
        val account = readers.create()
        val unplanned = books.create(account, planned = false)
        val planned = books.create(account)

        assertThatThrownBy { ask(unplanned) }.isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(ChatError.NO_PLAN)
        }
        assertThatThrownBy { ask(planned, itemKey = "없는 항목") }.isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(ChatError.SECTION_NOT_FOUND)
        }
        assertThatThrownBy {
            ask(TutorBooks.Shelved(account = account, id = UUID.randomUUID(), planId = null, keys = listOf("x", "y")))
        }.isInstanceOfSatisfying(
            GlossException::class.java,
        ) { assertThat(it.error).isEqualTo(ChatError.BOOK_NOT_READY) }
        assertThat(said(account)).isEmpty()
        assertThat(reserves(account)).isZero()
        // A refused turn leaves the conversation free.
        answers("답이에요.")
        assertThat(answered(ask(planned)).status).isEqualTo("done")
    }
}
