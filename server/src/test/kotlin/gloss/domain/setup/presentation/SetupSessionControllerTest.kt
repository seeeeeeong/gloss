package gloss.domain.setup.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.book.service.EditionCache
import gloss.domain.setup.TitleSearchModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class, TitleSearchModel.Config::class)
class SetupSessionControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val fakeBookSourceGateway: FakeBookSourceGateway,
    @Autowired private val editionCache: EditionCache,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @BeforeEach
    fun reset() {
        fakeBookSourceGateway.reset()
        editionCache.clear()
    }

    @Test
    fun `a reader names a book, picks an edition, edits its contents and starts, then the book stands confirmed`() {
        fakeBookSourceGateway.holds("101")
        val browser = signedIn()

        val started = browser.post("/api/me/setup-sessions", mapOf("text" to "오브젝트"))
        val sessionId = started.body?.get("id")?.asString()
        val shown = browser.get("/api/me/setup-sessions/$sessionId/messages/$BOOKS_SEQ/books")
        val picked =
            browser.post(
                "/api/me/setup-sessions/$sessionId/pick",
                mapOf("ref" to "101", "revision" to started.body?.get("revision")?.asInt()),
            )
        val seen =
            picked.body
                ?.get("revision")
                ?.asInt()
        val edited =
            browser.put(
                "/api/me/setup-sessions/$sessionId/contents",
                mapOf("toc" to "1장 객체\n2장 설계", "revision" to seen),
            )
        val revision =
            edited.body
                ?.get("revision")
                ?.asInt()
        val confirmed = browser.post("/api/me/setup-sessions/$sessionId/confirm", mapOf("revision" to revision))
        val bookId = confirmed.body?.get("bookId")?.asString()
        val book = browser.get("/api/me/books/$bookId")
        val again = browser.get("/api/me/setup-sessions/$sessionId")

        assertThat(started.status).isEqualTo(201)
        assertThat(
            shown.body
                ?.get(0)
                ?.get("title")
                ?.asString(),
        ).isEqualTo("오브젝트")
        assertThat(picked.body?.get("state")?.asString()).isEqualTo("book_chosen")
        assertThat(picked.body?.get("bookId")?.isNull).isTrue()
        assertThat(
            edited.body
                ?.get("held")
                ?.get("items")
                ?.size(),
        ).isEqualTo(2)
        assertThat(confirmed.body?.get("state")?.asString()).isEqualTo("confirmed")
        assertThat(book.body?.get("currentTocId")?.asString()).isNotNull()
        assertThat(
            book.body
                ?.get("toc")
                ?.get("raw")
                ?.asString(),
        ).isEqualTo("1장 객체\n2장 설계")
        val kinds: List<String> =
            again.body
                ?.get("messages")
                ?.toList()
                .orEmpty()
                .map { it.get("kind").asString() }
                .filterNot { it in MODEL }
        assertThat(kinds).containsExactly("text", "books", "pick", "contents", "confirm", "done")
    }

    @Test
    fun `an empty message, contents without a book and a step out of turn are refused`() {
        val browser = signedIn()
        val started = browser.post("/api/me/setup-sessions", mapOf("text" to "오브젝트"))
        val sessionId = started.body?.get("id")?.asString()

        assertThat(browser.post("/api/me/setup-sessions", mapOf("text" to " ")).status).isEqualTo(400)
        assertThat(browser.post("/api/me/setup-sessions/$sessionId/confirm", emptyMap<String, String>()).status)
            .isEqualTo(400)
        assertThat(browser.post("/api/me/setup-sessions/typed-contents", mapOf("title" to "책")).status).isEqualTo(400)
        assertThat(
            browser.post("/api/me/setup-sessions/typed-contents", mapOf("ref" to "101", "toc" to "1장")).code,
        ).isEqualTo("setup.book_not_offered")
        assertThat(
            browser
                .post(
                    "/api/me/setup-sessions/$sessionId/confirm",
                    mapOf("revision" to 1),
                ).code,
        ).isEqualTo("setup.no_book_yet")
        assertThat(browser.post("/api/me/setup-sessions/$sessionId/pick", mapOf("ref" to "999", "revision" to 1)).code)
            .isEqualTo("setup.book_not_offered")
    }

    @Test
    fun `a book typed in whole starts a session, and starting with it makes the book`() {
        val browser = signedIn()

        val held =
            browser.post(
                "/api/me/setup-sessions/typed-contents",
                mapOf("title" to "나의 노트", "authors" to listOf("나"), "toc" to "1장 시작\n1.1 준비"),
            )
        val sessionId = held.body?.get("id")?.asString()
        val revision =
            held.body
                ?.get("revision")
                ?.asInt()
        val started = browser.post("/api/me/setup-sessions/$sessionId/confirm", mapOf("revision" to revision))
        val book = browser.get("/api/me/books/${started.body?.get("bookId")?.asString()}")

        assertThat(held.status).isEqualTo(201)
        assertThat(
            held.body
                ?.get("held")
                ?.get("title")
                ?.asString(),
        ).isEqualTo("나의 노트")
        assertThat(started.body?.get("state")?.asString()).isEqualTo("confirmed")
        assertThat(book.body?.get("title")?.asString()).isEqualTo("나의 노트")
        assertThat(book.body?.get("currentTocId")?.asString()).isNotNull()
    }

    @Test
    fun `a session is its reader's own`() {
        val owner = signedIn()
        val sessionId =
            owner
                .post("/api/me/setup-sessions", mapOf("text" to "오브젝트"))
                .body
                ?.get("id")
                ?.asString()
        val stranger = signedIn()

        assertThat(stranger.get("/api/me/setup-sessions/$sessionId").code).isEqualTo("setup.not_found")
        assertThat(
            stranger.post("/api/me/setup-sessions/$sessionId/messages", mapOf("text" to "a", "revision" to 1)).status,
        ).isEqualTo(404)
    }

    @Test
    fun `a session needs a signed-in reader`() {
        val browser = Browser(port)
        browser.fetchCsrf()

        assertThat(browser.post("/api/me/setup-sessions", mapOf("text" to "오브젝트")).status).isEqualTo(401)
        assertThat(fakeBookSourceGateway.asked).isEmpty()
    }

    private companion object {
        // The model's own calls and the list it showed, between the reader's message and the pick.
        val MODEL = setOf("tool_calls", "books_shown")

        // The reader's message, the model's call, then what its search found.
        const val BOOKS_SEQ = 3
    }
}
