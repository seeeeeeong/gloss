package gloss.domain.post.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import gloss.domain.post.service.PostService
import gloss.domain.post.service.PostTokens
import gloss.domain.post.service.PublishRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.URLEncoder
import java.time.Clock
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class PostControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val postService: PostService,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)
    private val itemKey = "1장 객체 > 1.1 tcp/ip"

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    private fun query(key: String) = URLEncoder.encode(key, Charsets.UTF_8)

    /** A book added over HTTP, then a post written for it as generation would. */
    private fun bookWithPost(browser: Browser): String {
        val created = browser.post("/api/me/books", mapOf("title" to "오브젝트", "toc" to "1장 객체\n1.1 TCP/IP"))
        val bookId = UUID.fromString(created.body?.get("id")?.asString())
        val tocId =
            UUID.fromString(
                created.body
                    ?.get("toc")
                    ?.get("id")
                    ?.asString(),
            )
        val accountId =
            jdbc
                .sql("select account_id from book where id = :id")
                .param("id", bookId)
                .query(UUID::class.java)
                .single()
        val planId = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                       json_sha256, model, prompt_version, created_at)
                values (:id, 0, :account, :book, :toc, 1, '{}', repeat('b', 64), 'model-x', 'p1', now())
                """.trimIndent(),
            ).param("id", planId)
            .param("account", accountId)
            .param("book", bookId)
            .param("toc", tocId)
            .update()
        postService.publish(
            PublishRequest(
                accountId = accountId,
                bookId = bookId,
                itemKey = itemKey,
                planId = planId,
                contentMd = "# TCP/IP\n\n본문이다.",
                finishReason = "stop",
                model = "model-x",
                promptVersion = "p1",
                tokens = PostTokens(cacheHit = 0, cacheMiss = 100, completion = 200),
            ),
        )
        return bookId.toString()
    }

    @Test
    fun `a reader lists a book's posts and reads one by its section's key`() {
        val browser = signedIn()
        val bookId = bookWithPost(browser)

        val posts = browser.get("/api/me/books/$bookId/posts")
        val post = browser.get("/api/me/books/$bookId/post?itemKey=${query(itemKey)}")

        assertThat(posts.status).isEqualTo(200)
        assertThat(posts.body?.size()).isEqualTo(1)
        assertThat(
            posts.body
                ?.get(0)
                ?.get("itemKey")
                ?.asString(),
        ).isEqualTo(itemKey)
        assertThat(post.status).isEqualTo(200)
        assertThat(post.body?.get("contentMd")?.asString()).isEqualTo("# TCP/IP\n\n본문이다.")
        assertThat(post.body?.get("versionNo")?.asInt()).isEqualTo(1)
    }

    @Test
    fun `a reader counts the posts of every book on their shelf`() {
        val browser = signedIn()
        val bookId = bookWithPost(browser)

        val counts = browser.get("/api/me/post-counts")

        assertThat(counts.status).isEqualTo(200)
        assertThat(counts.body?.size()).isEqualTo(1)
        assertThat(
            counts.body
                ?.get(0)
                ?.get("bookId")
                ?.asString(),
        ).isEqualTo(bookId)
        assertThat(
            counts.body
                ?.get(0)
                ?.get("posts")
                ?.asLong(),
        ).isEqualTo(1L)
        assertThat(signedIn().get("/api/me/post-counts").body?.size()).isEqualTo(0)
    }

    @Test
    fun `a section without a post is not found`() {
        val browser = signedIn()
        val bookId = bookWithPost(browser)

        val missing = browser.get("/api/me/books/$bookId/post?itemKey=${query("2장 설계")}")

        assertThat(missing.status).isEqualTo(404)
        assertThat(missing.code).isEqualTo("post.not_found")
    }

    @Test
    fun `another reader's post is not found, and their list is empty`() {
        val bookId = bookWithPost(signedIn())
        val other = signedIn()

        val post = other.get("/api/me/books/$bookId/post?itemKey=${query(itemKey)}")
        val posts = other.get("/api/me/books/$bookId/posts")

        assertThat(post.status).isEqualTo(404)
        assertThat(post.code).isEqualTo("post.not_found")
        assertThat(posts.body?.size()).isEqualTo(0)
    }

    @Test
    fun `a request without a key is refused as invalid input`() {
        val browser = signedIn()
        val bookId = bookWithPost(browser)

        val refused = browser.get("/api/me/books/$bookId/post")

        assertThat(refused.status).isEqualTo(400)
        assertThat(refused.code).isEqualTo("common.invalid_input")
    }

    @Test
    fun `no one reads posts without signing in`() {
        val bookId = bookWithPost(signedIn())

        val refused = Browser(port).get("/api/me/books/$bookId/posts")

        assertThat(refused.status).isEqualTo(401)
    }
}
