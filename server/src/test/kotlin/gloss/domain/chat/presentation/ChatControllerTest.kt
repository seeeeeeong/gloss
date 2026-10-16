package gloss.domain.chat.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.chat.TutorBooks
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.FinishReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeLlmGateway.Config::class,
    KeyedReaders.Config::class,
    TutorBooks.Config::class,
)
class ChatControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val readers: KeyedReaders,
    @Autowired private val books: TutorBooks,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val json = JsonMapper()
    private val password = "correct horse battery"

    @BeforeEach
    fun reset() {
        fakeLlmGateway.reset()
    }

    private class Reader(
        val browser: Browser,
        val book: TutorBooks.Shelved,
    )

    /** A reader with a key and a limit, signed in over HTTP with a book to ask about. */
    private fun reader(): Reader {
        val account = readers.create()
        jdbc
            .sql("update account set password_hash = :hash where id = :id")
            .param("hash", encoder.encode(password))
            .param("id", account)
            .update()
        val loginId =
            jdbc
                .sql("select login_id from account where id = :id")
                .param("id", account)
                .query(String::class.java)
                .single()
        val browser = Browser(port).also { it.signIn(loginId, password) }
        return Reader(browser, books.create(account))
    }

    private data class Event(
        val name: String,
        val data: String,
    )

    private fun eventsOf(body: String): List<Event> =
        body
            .split("\n\n")
            .filter { it.startsWith("event: ") }
            .map { frame ->
                val lines = frame.lines()
                Event(lines[0].removePrefix("event: "), lines[1].removePrefix("data: "))
            }

    @Test
    fun `a turn streams as server-sent events, the reader's message, the answer's pieces, then the reply as kept`() {
        val reader = reader()
        fakeLlmGateway.answer = { onText, _ ->
            onText("자원을 ")
            onText("나눠요.\n둘째 줄")
            ChatResult(
                "자원을 나눠요.\n둘째 줄",
                FinishReason.STOP,
                TokenUsage(cacheHitTokens = 1_400, cacheMissTokens = 200, completionTokens = 300, reasoningTokens = 0),
            )
        }
        val path = "/api/me/books/${reader.book.id}/chat"

        val streamed =
            reader.browser.postForText(
                "$path/turns",
                mapOf("text" to "왜요?", "itemKey" to reader.book.keys[1]),
            )
        val events = eventsOf(streamed.body)
        val kept = reader.browser.get(path)

        assertThat(streamed.status).isEqualTo(200)
        assertThat(streamed.contentType).startsWith("text/event-stream")
        assertThat(events.map { it.name }).containsExactly("asked", "text", "text", "answered")
        assertThat(json.readTree(events[0].data).get("text").asString()).isEqualTo("왜요?")
        assertThat(json.readTree(events[2].data).get("text").asString()).isEqualTo("나눠요.\n둘째 줄")
        val answered = json.readTree(events[3].data)
        assertThat(answered.get("status").asString()).isEqualTo("done")
        assertThat(answered.get("text").asString()).isEqualTo("자원을 나눠요.\n둘째 줄")
        val messages = checkNotNull(kept.body?.get("messages"))
        val roles = (0 until messages.size()).map { messages.get(it).get("role").asString() }
        assertThat(roles).containsExactly("reader", "gloss")
    }

    @Test
    fun `a turn refused before anything is kept is a plain error`() {
        val reader = reader()

        val refused =
            reader.browser.post(
                "/api/me/books/${reader.book.id}/chat/turns",
                mapOf(
                    "text" to "왜요?",
                    "itemKey" to "없는 항목",
                ),
            )
        val blank = reader.browser.post("/api/me/books/${reader.book.id}/chat/turns", mapOf("text" to " "))

        assertThat(refused.status).isEqualTo(404)
        assertThat(refused.code).isEqualTo("chat.section_not_found")
        assertThat(blank.status).isEqualTo(400)
        assertThat(fakeLlmGateway.requests).isEmpty()
    }

    @Test
    fun `stopping with nothing under way is fine, and another reader's conversation is not theirs to read`() {
        val reader = reader()
        val stranger = reader()

        val stopped =
            reader.browser.post(
                "/api/me/books/${reader.book.id}/chat/stop",
                mapOf("turnId" to UUID.randomUUID()),
            )
        val read = stranger.browser.get("/api/me/books/${reader.book.id}/chat")
        val anonymous = Browser(port).get("/api/me/books/${reader.book.id}/chat")

        assertThat(stopped.status).isEqualTo(204)
        assertThat(read.body?.get("messages")?.size()).isZero()
        assertThat(anonymous.status).isEqualTo(401)
    }
}
