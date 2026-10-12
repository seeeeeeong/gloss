package gloss.book.presentation

import gloss.TestcontainersConfiguration
import gloss.account.Browser
import gloss.account.Members
import gloss.book.FakeBookSourceGateway
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
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class)
class BookSourceControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val fakeBookSourceGateway: FakeBookSourceGateway,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @BeforeEach
    fun reset() {
        fakeBookSourceGateway.reset()
    }

    @Test
    fun `a book is searched for, added from the edition picked and shown with its source`() {
        fakeBookSourceGateway.holds("101")
        val browser = signedIn()

        val found = browser.get("/api/me/books/search?query=%EC%98%A4%EB%B8%8C%EC%A0%9D%ED%8A%B8")
        val ref =
            found.body
                ?.get(0)
                ?.get("ref")
                ?.asString()
        val added = browser.post("/api/me/books/from-source", mapOf("ref" to ref))
        val bookId = added.body?.get("id")?.asString()
        val book = browser.get("/api/me/books/$bookId")

        assertThat(found.status).isEqualTo(200)
        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트", "101")
        assertThat(
            found.body
                ?.get(0)
                ?.get("hasContents")
                ?.asBoolean(),
        ).isTrue()
        assertThat(added.status).isEqualTo(201)
        assertThat(book.body?.get("source")?.asString()).isEqualTo("yes24")
        assertThat(book.body?.get("sourceUrl")?.asString()).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(
            book.body
                ?.get("toc")
                ?.get("origin")
                ?.asString(),
        ).isEqualTo("api")
    }

    @Test
    fun `a search without words, an add without a reference and an unknown edition are refused`() {
        val browser = signedIn()

        assertThat(browser.get("/api/me/books/search?query=%20").code).isEqualTo("book.invalid_query")
        assertThat(browser.post("/api/me/books/from-source", mapOf("ref" to " ")).status).isEqualTo(400)
        assertThat(
            browser.post("/api/me/books/from-source", mapOf("ref" to "999")).code,
        ).isEqualTo("book.source_book_not_found")
    }

    @Test
    fun `searching and adding from the source need a signed-in reader`() {
        val browser = Browser(port)
        browser.fetchCsrf()

        assertThat(browser.get("/api/me/books/search?query=a").status).isEqualTo(401)
        assertThat(browser.post("/api/me/books/from-source", mapOf("ref" to "101")).status).isEqualTo(401)
        assertThat(fakeBookSourceGateway.asked).isEmpty()
    }
}
