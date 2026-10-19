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
    private val usage =
        TokenUsage(cacheHitTokens = 1_400, cacheMissTokens = 200, completionTokens = 300, reasoningTokens = 0)

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
        val listed = stranger.browser.get("/api/me/books/${reader.book.id}/chat/sessions")
        val anonymous = Browser(port).get("/api/me/books/${reader.book.id}/chat")

        assertThat(stopped.status).isEqualTo(204)
        assertThat(read.body?.get("messages")?.size()).isZero()
        assertThat(read.body?.get("sessionId")?.isNull).isTrue()
        assertThat(listed.body?.size()).isZero()
        assertThat(anonymous.status).isEqualTo(401)
    }

    private fun asked(
        reader: Reader,
        body: Map<String, Any?>,
    ): String {
        val streamed = reader.browser.postForText("/api/me/books/${reader.book.id}/chat/turns", body)
        val asked = eventsOf(streamed.body).first { it.name == "asked" }
        return json.readTree(asked.data).get("sessionId").asString()
    }

    @Test
    fun `a reader lists a book's conversations, opens one, goes on with it and deletes it`() {
        val reader = reader()
        fakeLlmGateway.answer = { _, _ -> ChatResult("답이에요.", FinishReason.STOP, usage) }
        val path = "/api/me/books/${reader.book.id}/chat"
        val first = asked(reader, mapOf("text" to "처음", "itemKey" to reader.book.keys[1]))
        val again = asked(reader, mapOf("text" to "이어서", "sessionId" to first))
        val second = asked(reader, mapOf("text" to "새 대화"))

        val listed = reader.browser.get("$path/sessions")
        val opened = reader.browser.get("$path/sessions/$first")
        val latest = reader.browser.get(path)
        val deleted = reader.browser.delete("$path/sessions/$second")
        val gone = reader.browser.get("$path/sessions/$second")
        val after = reader.browser.get("$path/sessions")

        assertThat(again).isEqualTo(first)
        assertThat(second).isNotEqualTo(first)
        val rows = checkNotNull(listed.body)
        assertThat((0 until rows.size()).map { rows.get(it).get("id").asString() }).containsExactly(second, first)
        assertThat(rows.get(1).get("firstQuestion").asString()).isEqualTo("처음")
        assertThat(rows.get(1).get("itemKey").asString()).isEqualTo(reader.book.keys[1])
        assertThat(opened.body?.get("messages")?.size()).isEqualTo(4)
        assertThat(latest.body?.get("sessionId")?.asString()).isEqualTo(second)
        assertThat(deleted.status).isEqualTo(204)
        assertThat(gone.status).isEqualTo(404)
        assertThat(gone.code).isEqualTo("chat.session_not_found")
        assertThat(after.body?.size()).isEqualTo(1)
    }

    @Test
    fun `another reader's conversation is not found, to read, ask on or delete`() {
        val reader = reader()
        val stranger = reader()
        fakeLlmGateway.answer = { _, _ -> ChatResult("답이에요.", FinishReason.STOP, usage) }
        val session = asked(reader, mapOf("text" to "처음"))
        val path = "/api/me/books/${reader.book.id}/chat"

        val read = stranger.browser.get("$path/sessions/$session")
        val deleted = stranger.browser.delete("$path/sessions/$session")
        val onTheirs =
            stranger.browser.post(
                "/api/me/books/${stranger.book.id}/chat/turns",
                mapOf("text" to "끼어들기", "sessionId" to session),
            )
        val kept = reader.browser.get("$path/sessions/$session")

        assertThat(read.status).isEqualTo(404)
        assertThat(deleted.status).isEqualTo(404)
        assertThat(onTheirs.status).isEqualTo(404)
        assertThat(onTheirs.code).isEqualTo("chat.session_not_found")
        assertThat(kept.body?.get("messages")?.size()).isEqualTo(2)
    }

    @Test
    fun `deleting a conversation leaves what its calls cost in the reader's usage`() {
        val reader = reader()
        fakeLlmGateway.answer = { _, _ -> ChatResult("답이에요.", FinishReason.STOP, usage) }
        val session = asked(reader, mapOf("text" to "처음"))
        val before = ledgerRows(reader)

        val deleted = reader.browser.delete("/api/me/books/${reader.book.id}/chat/sessions/$session")

        assertThat(deleted.status).isEqualTo(204)
        assertThat(before).isEqualTo(2)
        assertThat(ledgerRows(reader)).isEqualTo(before)
    }

    private fun ledgerRows(reader: Reader): Int =
        jdbc
            .sql("select count(*) from usage_ledger where account_id = :id")
            .param("id", reader.book.account)
            .query(Int::class.java)
            .single()
}
