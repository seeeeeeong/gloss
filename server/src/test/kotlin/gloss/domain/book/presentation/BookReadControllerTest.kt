package gloss.domain.book.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import gloss.domain.book.ConfirmedBooks
import gloss.domain.book.service.BookService
import gloss.domain.book.service.TocService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class BookReadControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
    @Autowired bookService: BookService,
    @Autowired tocService: TocService,
) {
    private val members = Members(jdbc, encoder, clock)
    private val confirmedBooks = ConfirmedBooks(jdbc, bookService, tocService)

    private class Reader(
        val browser: Browser,
        val bookId: String,
        val keys: List<String>,
    )

    private fun Browser.Response.field(name: String): String? = body?.get(name)?.asString()

    /** Signed in over HTTP, with a book added and its contents confirmed as setup confirms them. */
    private fun reader(): Reader {
        val browser = Browser(port).also { it.signIn(members.create(), members.password) }
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 티켓 판매\n2장 설계"))
        val toc = checkNotNull(created.body).get("toc")
        val bookId = checkNotNull(created.field("id"))
        confirmedBooks.confirm(bookId, toc.get("id").asString())
        val items = toc.get("items")
        val keys = (0 until items.size()).map { items.get(it).get("itemKey").asString() }
        return Reader(browser, bookId, keys)
    }

    @Test
    fun `a reader keeps where they are in a book and finds it listed among the books read lately`() {
        val reader = reader()

        val before = reader.browser.get("/api/me/books/${reader.bookId}/read")
        val kept = reader.browser.put("/api/me/books/${reader.bookId}/read", mapOf("itemKey" to reader.keys[1]))
        val found = reader.browser.get("/api/me/books/${reader.bookId}/read")
        val recent = reader.browser.get("/api/me/books/recent")

        assertThat(before.status).isEqualTo(404)
        assertThat(before.code).isEqualTo("book.not_read")
        assertThat(kept.status).isEqualTo(204)
        assertThat(found.field("itemKey")).isEqualTo(reader.keys[1])
        assertThat(recent.status).isEqualTo(200)
        val first = recent.body?.get(0)
        assertThat(first?.get("bookId")?.asString()).isEqualTo(reader.bookId)
        assertThat(first?.get("title")?.asString()).isEqualTo("오브젝트")
        assertThat(first?.get("itemTitle")?.asString()).isEqualTo("티켓 판매")
    }

    @Test
    fun `a place without an item is refused`() {
        val reader = reader()

        val blank = reader.browser.put("/api/me/books/${reader.bookId}/read", mapOf("itemKey" to " "))

        assertThat(blank.status).isEqualTo(400)
        assertThat(blank.code).isEqualTo("common.invalid_input")
    }

    @Test
    fun `another reader's book is not theirs to read`() {
        val reader = reader()
        val stranger = reader()
        reader.browser.put("/api/me/books/${reader.bookId}/read", mapOf("itemKey" to reader.keys[1]))

        val kept = stranger.browser.put("/api/me/books/${reader.bookId}/read", mapOf("itemKey" to reader.keys[1]))
        val found = stranger.browser.get("/api/me/books/${reader.bookId}/read")
        val recent = stranger.browser.get("/api/me/books/recent")
        val anonymous = Browser(port).get("/api/me/books/recent")

        assertThat(kept.status).isEqualTo(404)
        assertThat(kept.code).isEqualTo("book.not_found")
        assertThat(found.status).isEqualTo(404)
        assertThat(recent.body?.size()).isZero()
        assertThat(anonymous.status).isEqualTo(401)
    }
}
