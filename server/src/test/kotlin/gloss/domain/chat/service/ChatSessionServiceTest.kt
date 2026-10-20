package gloss.domain.chat.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.chat.TutorBooks
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.llm.KeyedReaders
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
class ChatSessionServiceTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val chatSessionService: ChatSessionService,
    @Autowired private val readers: KeyedReaders,
    @Autowired private val books: TutorBooks,
    @Autowired private val jdbc: JdbcClient,
) {
    private fun done(text: String) =
        Answer(status = ChatStatus.DONE, text = text, finishReason = "stop", model = "deepseek-flash")

    // The conversation each book's turns go on; a book's first turn starts it.
    private val sessions = mutableMapOf<UUID, UUID>()

    private fun open(
        book: TutorBooks.Shelved,
        request: ChatAskRequest,
    ): Turn {
        val turn = chatService.open(book.account, book.id, request.copy(sessionId = sessions[book.id]))
        sessions[book.id] = turn.session.id
        return turn
    }

    private fun turn(
        book: TutorBooks.Shelved,
        request: ChatAskRequest,
        answer: Answer = done("답이에요."),
    ): Turn {
        val turn = open(book, request)
        chatService.close(turn, answer)
        chatService.end(turn)
        return turn
    }

    @Test
    fun `a book's conversations are listed newest activity first, each by its first question`() {
        val book = books.create(readers.create())
        val older = turn(book, ChatAskRequest(text = "처음 질문", itemKey = book.keys[1]))
        sessions.remove(book.id)
        val newer = turn(book, ChatAskRequest(text = "표지에서 물은 질문"))
        sessions[book.id] = older.session.id
        turn(book, ChatAskRequest(text = "이어서", itemKey = book.keys[2]))

        val listed = chatSessionService.sessions(book.account, book.id)

        assertThat(listed.map { it.id }).containsExactly(older.session.id, newer.session.id)
        assertThat(listed.first().firstQuestion).isEqualTo("처음 질문")
        assertThat(listed.first().itemKey).isEqualTo(book.keys[1])
        assertThat(listed.first().itemKeys).containsExactlyInAnyOrder(book.keys[1], book.keys[2])
        assertThat(listed.first().lastActivityAt).isAfter(listed.first().startedAt)
        assertThat(listed.last().itemKey).isNull()
        assertThat(listed.last().itemKeys).isEmpty()
        assertThat(chatSessionService.latest(book.account, book.id).sessionId).isEqualTo(older.session.id)
        assertThat(chatSessionService.sessions(readers.create(), book.id)).isEmpty()
    }

    @Test
    fun `the latest conversations of no books are none, and of too many books are refused`() {
        val account = readers.create()

        assertThat(chatSessionService.latestOf(account, emptyList())).isEmpty()
        assertThatThrownBy { chatSessionService.latestOf(account, List(21) { UUID.randomUUID() }) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(ChatError.TOO_MANY_BOOKS)
            }
    }

    @Test
    fun `before any question there is no conversation`() {
        val book = books.create(readers.create())

        val latest = chatSessionService.latest(book.account, book.id)

        assertThat(latest.sessionId).isNull()
        assertThat(latest.messages).isEmpty()
        assertThat(chatSessionService.sessions(book.account, book.id)).isEmpty()
    }

    @Test
    fun `a conversation is read, continued or deleted only by its reader, and only under its book`() {
        val book = books.create(readers.create())
        val other = books.create(book.account)
        val session = turn(book, ChatAskRequest(text = "하나")).session.id
        val stranger = readers.create()

        assertNotFound { chatSessionService.conversation(stranger, book.id, session) }
        assertNotFound { chatSessionService.conversation(book.account, other.id, session) }
        assertNotFound { chatSessionService.delete(stranger, book.id, session) }
        assertNotFound {
            chatService.open(book.account, other.id, ChatAskRequest(text = "둘", sessionId = session))
        }
        assertThat(chatSessionService.conversation(book.account, book.id, session).messages).hasSize(2)
    }

    @Test
    fun `deleting a conversation removes it whole and leaves the book's others`() {
        val book = books.create(readers.create())
        val deleted = turn(book, ChatAskRequest(text = "지울 질문")).session.id
        turn(book, ChatAskRequest(text = "지울 질문 둘"))
        sessions.remove(book.id)
        val kept = turn(book, ChatAskRequest(text = "남길 질문")).session.id

        chatSessionService.delete(book.account, book.id, deleted)

        assertThat(chatSessionService.sessions(book.account, book.id).map { it.id }).containsExactly(kept)
        assertThat(chatSessionService.latest(book.account, book.id).sessionId).isEqualTo(kept)
        assertNotFound { chatSessionService.conversation(book.account, book.id, deleted) }
        assertNotFound {
            chatService.open(book.account, book.id, ChatAskRequest(text = "다시", sessionId = deleted))
        }
        val left =
            jdbc
                .sql("select count(*) from chat_message where session_id = :id")
                .param("id", deleted)
                .query(Int::class.java)
                .single()
        assertThat(left).isZero()
    }

    @Test
    fun `a conversation is not deleted while a turn of it is under way`() {
        val book = books.create(readers.create())
        val open = open(book, ChatAskRequest(text = "하나"))

        assertThatThrownBy { chatSessionService.delete(book.account, book.id, open.session.id) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(ChatError.STILL_ANSWERING)
            }
        chatService.close(open, done("답"))
        chatService.end(open)
        chatSessionService.delete(book.account, book.id, open.session.id)

        assertThat(chatSessionService.sessions(book.account, book.id)).isEmpty()
    }

    private fun assertNotFound(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(ChatError.SESSION_NOT_FOUND)
        }
    }
}
