package gloss.post.presentation

import gloss.TestcontainersConfiguration
import gloss.account.Browser
import gloss.account.Members
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.URLEncoder
import java.time.Clock
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class PostGenerationControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    // The worker does not run in tests, so the jobs asked for here wait; another test's worker would take them.
    @AfterEach
    fun emptyQueue() {
        jdbc.sql("delete from generation_job where kind = 'plan'").update()
    }

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    private fun query(key: String) = URLEncoder.encode(key, Charsets.UTF_8)

    private fun planJobs(bookId: String): Int =
        jdbc
            .sql("select count(*) from generation_job where kind = 'plan' and payload ->> 'bookId' = :book")
            .param("book", bookId)
            .query(Int::class.java)
            .single()

    /** A book added and confirmed over HTTP; the confirm's own plan job is waited for, so none arrives later. */
    private fun confirmedBook(browser: Browser): Pair<String, String> {
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 TCP/IP"))
        val book = checkNotNull(created.body)
        val bookId = book.get("id").asString()
        val toc = book.get("toc")
        browser.post("/api/me/books/$bookId/toc/${toc.get("id").asString()}/confirm", null)
        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        await().atMost(Duration.ofSeconds(10)).until { planJobs(bookId) == 1 }
        return bookId to
            toc
                .get("items")
                .get(1)
                .get("itemKey")
                .asString()
    }

    @Test
    fun `a reader asks for a section's post and is told the plan comes first, without a second plan job`() {
        val browser = signedIn()
        val (bookId, itemKey) = confirmedBook(browser)

        val asked = browser.post("/api/me/books/$bookId/post/generation?itemKey=${query(itemKey)}", null)
        val retried = browser.post("/api/me/books/$bookId/post/generation?itemKey=${query(itemKey)}&retry=true", null)

        assertThat(asked.status).isEqualTo(200)
        assertThat(asked.body?.get("state")?.asString()).isEqualTo("planning")
        assertThat(retried.body?.get("state")?.asString()).isEqualTo("planning")
        assertThat(planJobs(bookId)).isEqualTo(1)
    }

    @Test
    fun `a section the book does not have, or of another reader's book, is not found`() {
        val browser = signedIn()
        val (bookId, itemKey) = confirmedBook(browser)

        val missing = browser.post("/api/me/books/$bookId/post/generation?itemKey=${query("2장 설계")}", null)
        val theirs = signedIn().post("/api/me/books/$bookId/post/generation?itemKey=${query(itemKey)}", null)

        assertThat(missing.status).isEqualTo(404)
        assertThat(missing.code).isEqualTo("post.section_not_found")
        assertThat(theirs.status).isEqualTo(404)
    }

    @Test
    fun `asking without a key or without signing in is refused`() {
        val browser = signedIn()
        val (bookId, itemKey) = confirmedBook(browser)

        val keyless = browser.post("/api/me/books/$bookId/post/generation", null)
        val anonymous = Browser(port).post("/api/me/books/$bookId/post/generation?itemKey=${query(itemKey)}", null)

        assertThat(keyless.status).isEqualTo(400)
        assertThat(anonymous.status).isIn(401, 403)
    }
}
