package gloss.domain.chat.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.chat.TutorBooks
import gloss.domain.chat.domain.ChatHistoryPolicy
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.chat.Role
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

// Readers and books made here stay: their messages are append-only.
@SpringBootTest
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    KeyedReaders.Config::class,
    TutorBooks.Config::class,
)
class ChatServiceTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val readers: KeyedReaders,
    @Autowired private val books: TutorBooks,
    @Autowired private val jdbc: JdbcClient,
) {
    private fun done(text: String) =
        Answer(status = ChatStatus.DONE, text = text, finishReason = "stop", model = "deepseek-flash")

    private fun turn(
        book: TutorBooks.Shelved,
        request: ChatAskRequest,
        answer: Answer = done("답이에요."),
    ): Turn {
        val turn = chatService.open(book.account, book.id, request)
        chatService.close(turn, answer)
        chatService.end(turn)
        return turn
    }

    @Test
    fun `a reader's conversation is theirs alone, one for each book`() {
        val book = books.create(readers.create())
        val other = books.create(book.account)
        turn(book, ChatAskRequest(text = "질문", itemKey = book.keys[1]))
        val stranger = readers.create()

        assertThat(chatService.conversation(book.account, book.id).messages).hasSize(2)
        assertThat(chatService.conversation(book.account, other.id).messages).isEmpty()
        assertThat(chatService.conversation(stranger, book.id).messages).isEmpty()
    }

    @Test
    fun `a turn keeps the phrase on one line, and the prompt exactly as it is sent`() {
        val book = books.create(readers.create())

        val turnId = UUID.randomUUID()
        val request =
            ChatAskRequest(text = "  무슨 뜻인가요?\n", itemKey = book.keys[1], quote = "자원 \n 할당자로", turnId = turnId)
        val turn = turn(book, request)
        val asked = chatService.conversation(book.account, book.id).messages.first()
        val prompt =
            jdbc
                .sql("select prompt from chat_message where account_id = :id and seq = 1")
                .param("id", book.account)
                .query(String::class.java)
                .single()

        assertThat(asked.quote).isEqualTo("자원 할당자로")
        assertThat(asked.text).isEqualTo("무슨 뜻인가요?")
        assertThat(asked.turnId).isEqualTo(turnId)
        assertThat(
            turn.call.messages
                .last()
                .role,
        ).isEqualTo(Role.USER)
        assertThat(
            turn.call.messages
                .last()
                .content,
        ).isEqualTo(prompt)
        assertThat(prompt).contains("인용: 자원 할당자로").endsWith("질문: 무슨 뜻인가요?")
    }

    @Test
    fun `a post goes in once a version, so a rewritten one goes in again`() {
        val book = books.create(readers.create())
        books.write(book, book.keys[1], "## 학습 목표\n\n처음 쓴 글이다.")
        val first = turn(book, ChatAskRequest(text = "하나", itemKey = book.keys[1]))
        val again = turn(book, ChatAskRequest(text = "둘", itemKey = book.keys[1]))
        books.write(book, book.keys[1], "## 학습 목표\n\n다시 쓴 글이다.")
        val rewritten = turn(book, ChatAskRequest(text = "셋", itemKey = book.keys[1]))

        assertThat(
            first.call.messages
                .last()
                .content,
        ).contains("처음 쓴 글이다.")
        assertThat(
            again.call.messages
                .last()
                .content,
        ).doesNotContain("처음 쓴 글이다.")
        assertThat(
            rewritten.call.messages
                .last()
                .content,
        ).contains("다시 쓴 글이다.")
    }

    @Test
    fun `a post that went in with a turn not finished goes in again with the next`() {
        val book = books.create(readers.create())
        books.write(book, book.keys[1], "## 학습 목표\n\n글이다.")
        val failed = Answer.failed(errorCode = "llm.provider_failed", streamed = "", model = "deepseek-flash")
        turn(book, ChatAskRequest(text = "하나", itemKey = book.keys[1]), failed)

        val next = turn(book, ChatAskRequest(text = "둘", itemKey = book.keys[1]))

        assertThat(next.call.messages).hasSize(2)
        assertThat(
            next.call.messages
                .last()
                .content,
        ).contains("글이다.")
    }

    @Test
    fun `a question from no item names none and carries no post`() {
        val book = books.create(readers.create())
        books.write(book, book.keys[1], "## 학습 목표\n\n글이다.")

        val turn = turn(book, ChatAskRequest(text = "이 책은 누구를 위한 책인가요?"))

        assertThat(
            turn.call.messages
                .last()
                .content,
        ).isEqualTo("질문: 이 책은 누구를 위한 책인가요?")
        assertThat(chatService.conversation(book.account, book.id).messages.map { it.itemKey }).containsOnlyNulls()
    }

    @Test
    fun `a failed reply tells the reader in the error's own words, or that no answer came`() {
        val book = books.create(readers.create())
        turn(
            book,
            ChatAskRequest(text = "하나"),
            Answer.failed(errorCode = "budget.too_frequent", streamed = "", model = "m"),
        )
        turn(book, ChatAskRequest(text = "둘"), Answer.failed(errorCode = "error", streamed = "", model = "m"))

        val replies = chatService.conversation(book.account, book.id).messages.filter { it.role == "gloss" }

        assertThat(replies.map { it.message }).containsExactly(
            "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.",
            "답을 받지 못했어요. 다시 물어 주세요.",
        )
        assertThat(replies.map { it.code }).containsExactly("budget.too_frequent", "error")
    }

    @Test
    fun `a conversation is held from a turn's opening to its end, and is free after`() {
        val book = books.create(readers.create())
        val open = chatService.open(book.account, book.id, ChatAskRequest(text = "하나"))

        assertThatThrownBy { chatService.open(book.account, book.id, ChatAskRequest(text = "둘")) }
            .isInstanceOfSatisfying(GlossException::class.java) { assertThat(it.error).isEqualTo(ChatError.ANSWERING) }
        chatService.close(open, done("답"))
        chatService.end(open)
        val next = turn(book, ChatAskRequest(text = "셋"))

        assertThat(next.asked.seq).isEqualTo(3)
        assertThat(
            chatService.conversation(book.account, book.id).messages.map {
                it.text
            },
        ).containsExactly("하나", "답", "셋", "답이에요.")
    }

    @Test
    fun `a long conversation sends a bounded history, read from where the last turn's started`() {
        val book = books.create(readers.create())
        // About 6,000 bytes a reply: the history outgrows its budget every eight turns or so.
        val long = "자원을 나눠 주는 역할이에요. ".repeat(130)

        val calls = (1..30).map { turn(book, ChatAskRequest(text = "질문 $it"), done(long)).call.messages }
        val starts =
            jdbc
                .sql("select history_from from chat_message where account_id = :id and role = 'reader' order by seq")
                .param("id", book.account)
                .query(Int::class.java)
                .list()

        assertThat(calls).allSatisfy { messages ->
            val history = messages.drop(1).dropLast(1).sumOf { ChatHistoryPolicy.tokensOf(it.content) + 8 }
            assertThat(history).isLessThanOrEqualTo(ChatHistoryPolicy.HISTORY_TOKENS)
        }
        assertThat(calls.last().last().content).isEqualTo("질문: 질문 30")
        assertThat(starts.distinct().size).isBetween(3, 8)
        assertThat(starts).isSorted()
    }

    @Test
    fun `a question is about the version of the post the reader was shown, though a newer one was written`() {
        val book = books.create(readers.create())
        val shown = books.write(book, book.keys[1], "## 학습 목표\n\n화면에 있던 글이다.")
        books.write(book, book.keys[1], "## 학습 목표\n\n다른 탭에서 다시 쓴 글이다.")

        val pinned = turn(book, ChatAskRequest(text = "하나", itemKey = book.keys[1], postVersionId = shown))
        val current = turn(book, ChatAskRequest(text = "둘", itemKey = book.keys[1]))
        val stranger = turn(book, ChatAskRequest(text = "셋", itemKey = book.keys[2], postVersionId = shown))

        assertThat(
            pinned.call.messages
                .last()
                .content,
        ).contains("화면에 있던 글이다.").doesNotContain("다시 쓴 글이다.")
        assertThat(pinned.asked.asked?.postVersionId).isEqualTo(shown)
        assertThat(
            current.call.messages
                .last()
                .content,
        ).contains("다른 탭에서 다시 쓴 글이다.")
        assertThat(
            stranger.call.messages
                .last()
                .content,
        ).doesNotContain("화면에 있던 글이다.")
    }

    @Test
    fun `the conversation says a turn is under way until it ends, for any page that reads it`() {
        val book = books.create(readers.create())
        val before = chatService.conversation(book.account, book.id).answering
        val open = chatService.open(book.account, book.id, ChatAskRequest(text = "하나"))

        val during = chatService.conversation(book.account, book.id)
        chatService.close(open, done("답"))
        val kept = chatService.conversation(book.account, book.id).answering
        chatService.end(open)
        val after = chatService.conversation(book.account, book.id).answering

        assertThat(before).isFalse()
        assertThat(during.answering).isTrue()
        assertThat(during.messages.map { it.role }).containsExactly("reader")
        assertThat(kept).isTrue()
        assertThat(after).isFalse()
    }
}
