package gloss.domain.plan.presentation

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
class PlanControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
    @Autowired bookService: BookService,
    @Autowired tocService: TocService,
) {
    private val members = Members(jdbc, encoder, clock)
    private val confirmedBooks = ConfirmedBooks(jdbc, bookService, tocService)

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @Test
    fun `a reader waits on their confirmed book's plan, and another reader finds no such book`() {
        val browser = signedIn()
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 책임"))
        val bookId = created.body?.get("id")?.asString()
        val tocId =
            created.body
                ?.get("toc")
                ?.get("id")
                ?.asString()
        confirmedBooks.confirm(checkNotNull(bookId), checkNotNull(tocId))

        val status = browser.get("/api/me/books/$bookId/plan")
        val stranger = signedIn().get("/api/me/books/$bookId/plan")

        assertThat(status.status).isEqualTo(200)
        assertThat(status.body?.get("state")?.asString()).isEqualTo("planning")
        assertThat(stranger.status).isEqualTo(404)
        assertThat(stranger.code).isEqualTo("plan.book_not_found")
        val retried = browser.post("/api/me/books/$bookId/plan/retry", emptyMap<String, String>())
        assertThat(retried.body?.get("state")?.asString()).isEqualTo("planning")
    }
}
