package gloss.book.presentation

import gloss.TestcontainersConfiguration
import gloss.account.Browser
import gloss.account.Members
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
    fun `another reader's book is not found`() {
        val created = signedIn().post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체"))

        val other = signedIn().get("/api/me/books/${created.body?.get("id")?.asString()}")

        assertThat(other.status).isEqualTo(404)
        assertThat(other.code).isEqualTo("book.not_found")
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
