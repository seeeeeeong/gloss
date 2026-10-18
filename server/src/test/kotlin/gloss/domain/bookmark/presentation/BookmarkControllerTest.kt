package gloss.domain.bookmark.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
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
class BookmarkControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    private class Reader(
        val browser: Browser,
        val bookId: String,
        val keys: List<String>,
    )

    private fun Browser.Response.field(name: String): String? = body?.get(name)?.asString()

    /** Signed in over HTTP, with a book added and its contents confirmed as the page does it. */
    private fun reader(): Reader {
        val browser = Browser(port).also { it.signIn(members.create(), members.password) }
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 티켓 판매\n2장 설계"))
        val bookId = checkNotNull(created.field("id"))
        val tocId =
            checkNotNull(
                created.body
                    ?.get("toc")
                    ?.get("id")
                    ?.asString(),
            )
        val confirmed = browser.post("/api/me/books/$bookId/toc/$tocId/confirm")
        val items =
            checkNotNull(
                confirmed.body
                    ?.get("toc")
                    ?.get("items"),
            )
        val keys = (0 until items.size()).map { items.get(it).get("itemKey").asString() }
        return Reader(browser, bookId, keys)
    }

    private fun save(
        reader: Reader,
        bookId: String = reader.bookId,
        quote: String? = null,
    ) = reader.browser.post(
        "/api/me/bookmarks",
        mapOf("bookId" to bookId, "itemKey" to reader.keys[1], "quote" to quote),
    )

    @Test
    fun `a reader saves a phrase, finds it listed, and deletes it`() {
        val reader = reader()

        val saved = save(reader, quote = "관람객의 가방")
        val id = checkNotNull(saved.field("id"))
        val listed = reader.browser.get("/api/me/bookmarks")
        val deleted = reader.browser.delete("/api/me/bookmarks/$id")
        val after = reader.browser.get("/api/me/bookmarks")

        assertThat(saved.status).isEqualTo(201)
        assertThat(saved.field("bookTitle")).isEqualTo("오브젝트")
        assertThat(saved.field("itemKey")).isEqualTo(reader.keys[1])
        assertThat(saved.field("itemNumber")).isEqualTo("1.1")
        assertThat(saved.field("itemTitle")).isEqualTo("티켓 판매")
        assertThat(saved.field("quote")).isEqualTo("관람객의 가방")
        assertThat(listed.status).isEqualTo(200)
        assertThat(listed.body?.size()).isEqualTo(1)
        assertThat(
            listed.body
                ?.get(0)
                ?.get("id")
                ?.asString(),
        ).isEqualTo(id)
        assertThat(deleted.status).isEqualTo(204)
        assertThat(after.body?.size()).isZero()
    }

    @Test
    fun `a bookmark without an item is refused`() {
        val reader = reader()

        val blank =
            reader.browser.post(
                "/api/me/bookmarks",
                mapOf("bookId" to reader.bookId, "itemKey" to " "),
            )

        assertThat(blank.status).isEqualTo(400)
        assertThat(blank.code).isEqualTo("common.invalid_input")
    }

    @Test
    fun `another reader's bookmarks and books are not theirs to touch`() {
        val reader = reader()
        val stranger = reader()
        val saved = save(reader)
        val id = checkNotNull(saved.field("id"))

        val deleted = stranger.browser.delete("/api/me/bookmarks/$id")
        val onTheirBook = save(stranger, bookId = reader.bookId)
        val listed = stranger.browser.get("/api/me/bookmarks")
        val kept = reader.browser.get("/api/me/bookmarks")
        val anonymous = Browser(port).get("/api/me/bookmarks")

        assertThat(deleted.status).isEqualTo(404)
        assertThat(deleted.code).isEqualTo("bookmark.not_found")
        assertThat(onTheirBook.status).isEqualTo(404)
        assertThat(onTheirBook.code).isEqualTo("bookmark.book_not_ready")
        assertThat(listed.body?.size()).isZero()
        assertThat(kept.body?.size()).isEqualTo(1)
        assertThat(anonymous.status).isEqualTo(401)
    }
}
