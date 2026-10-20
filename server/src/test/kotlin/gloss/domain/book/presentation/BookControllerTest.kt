package gloss.domain.book.presentation

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
class BookControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @Test
    fun `a book is added, edited, confirmed and listed on the reader's shelf`() {
        val browser = signedIn()

        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 티켓"))
        val bookId = created.body?.get("id")?.asString()
        val revised = browser.post("/api/me/books/$bookId/toc", mapOf("toc" to "1장 객체\n1.1 티켓\n1.2 문제"))
        val tocId =
            revised.body
                ?.get("toc")
                ?.get("id")
                ?.asString()
        val confirmed = browser.post("/api/me/books/$bookId/toc/$tocId/confirm")
        val shelf = browser.get("/api/me/books")

        assertThat(created.status).isEqualTo(201)
        assertThat(
            revised.body
                ?.get("toc")
                ?.get("versionNo")
                ?.asInt(),
        ).isEqualTo(2)
        assertThat(confirmed.body?.get("currentTocId")?.asString()).isEqualTo(tocId)
        assertThat(shelf.body?.size()).isEqualTo(1)
        assertThat(
            shelf.body
                ?.get(0)
                ?.get("title")
                ?.asString(),
        ).isEqualTo("오브젝트")
        assertThat(
            browser
                .get("/api/me/books/$bookId")
                .body
                ?.get("toc")
                ?.get("items")
                ?.size(),
        ).isEqualTo(3)
    }

    @Test
    fun `the contents in use are served once confirmed, and not before`() {
        val browser = signedIn()
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 티켓"))
        val bookId = created.body?.get("id")?.asString()
        val tocId =
            created.body
                ?.get("toc")
                ?.get("id")
                ?.asString()

        val before = browser.get("/api/me/books/$bookId/toc/current")
        browser.post("/api/me/books/$bookId/toc/$tocId/confirm")
        browser.post("/api/me/books/$bookId/toc", mapOf("toc" to "1장 다시 쓴 목차"))
        val current = browser.get("/api/me/books/$bookId/toc/current")

        assertThat(before.status).isEqualTo(404)
        assertThat(before.code).isEqualTo("book.toc_not_confirmed")
        assertThat(current.body?.get("id")?.asString()).isEqualTo(tocId)
        assertThat(current.body?.get("items")?.size()).isEqualTo(2)
    }

    @Test
    fun `another reader's book is not found`() {
        val created = signedIn().post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체"))

        val other = signedIn().get("/api/me/books/${created.body?.get("id")?.asString()}")

        assertThat(other.status).isEqualTo(404)
        assertThat(other.code).isEqualTo("book.not_found")
    }

    @Test
    fun `a reader takes a book off their shelf, and no one else can`() {
        val reader = signedIn()
        val stranger = signedIn()
        val created = reader.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체"))
        val bookId = created.body?.get("id")?.asString()

        val theirs = stranger.delete("/api/me/books/$bookId")
        val archived = reader.delete("/api/me/books/$bookId")
        val gone = reader.get("/api/me/books/$bookId")
        val shelf = reader.get("/api/me/books")
        val again = reader.delete("/api/me/books/$bookId")

        assertThat(theirs.status).isEqualTo(404)
        assertThat(theirs.code).isEqualTo("book.not_found")
        assertThat(archived.status).isEqualTo(204)
        assertThat(gone.status).isEqualTo(404)
        assertThat(shelf.body?.size()).isZero()
        assertThat(again.status).isEqualTo(404)
    }

    @Test
    fun `a book without a title or contents is a bad request`() {
        val answer = signedIn().post("/api/me/books", mapOf("title" to "", "toc" to "1장"))

        assertThat(answer.status).isEqualTo(400)
    }

    @Test
    fun `no one signed out sees a shelf`() {
        assertThat(Browser(port).get("/api/me/books").status).isEqualTo(401)
    }
}
