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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(
    TestcontainersConfiguration::class,
    FakeKeyCheckGateway.Config::class,
    FakeLlmGateway.Config::class,
    KeyedReaders.Config::class,
    TutorBooks.Config::class,
)
class RecentChatControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val readers: KeyedReaders,
    @Autowired private val books: TutorBooks,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val encoder: PasswordEncoder,
) {
    private val password = "correct horse battery"
    private val usage =
        TokenUsage(cacheHitTokens = 1_400, cacheMissTokens = 200, completionTokens = 300, reasoningTokens = 0)

    @BeforeEach
    fun reset() {
        fakeLlmGateway.reset()
        fakeLlmGateway.answer = { _, _ -> ChatResult("답이에요.", FinishReason.STOP, usage) }
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

    private fun ask(
        reader: Reader,
        bookId: UUID,
        text: String,
    ) {
        reader.browser.postForText("/api/me/books/$bookId/chat/turns", mapOf("text" to text))
    }

    @Test
    fun `a reader lists the latest conversations of the books they name, each with its book`() {
        val reader = reader()
        val other = books.create(reader.book.account)
        repeat(4) { ask(reader, reader.book.id, "질문 $it") }
        ask(reader, other.id, "다른 책")

        val listed = reader.browser.get("/api/me/chat/recent?bookIds=${reader.book.id},${other.id}")

        val rows = checkNotNull(listed.body)
        val asked = (0 until rows.size()).map { rows.get(it).get("firstQuestion").asString() }
        assertThat(listed.status).isEqualTo(200)
        assertThat(asked).containsExactly("다른 책", "질문 3", "질문 2", "질문 1")
        assertThat(rows.get(0).get("bookId").asString()).isEqualTo(other.id.toString())
    }

    @Test
    fun `another reader's books have no conversations to list, and too many books are refused`() {
        val reader = reader()
        val stranger = reader()
        ask(reader, reader.book.id, "처음")
        val many = List(21) { UUID.randomUUID() }.joinToString(",")

        val theirs = stranger.browser.get("/api/me/chat/recent?bookIds=${reader.book.id}")
        val tooMany = reader.browser.get("/api/me/chat/recent?bookIds=$many")
        val anonymous = Browser(port).get("/api/me/chat/recent?bookIds=${reader.book.id}")

        assertThat(theirs.body?.size()).isZero()
        assertThat(tooMany.status).isEqualTo(400)
        assertThat(tooMany.code).isEqualTo("chat.too_many_books")
        assertThat(anonymous.status).isEqualTo(401)
    }
}
