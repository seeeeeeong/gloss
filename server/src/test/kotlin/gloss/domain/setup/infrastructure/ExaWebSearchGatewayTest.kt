package gloss.domain.setup.infrastructure

import com.sun.net.httpserver.HttpServer
import gloss.domain.setup.domain.WebSearchFailed
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress

class ExaWebSearchGatewayTest {
    private var server: HttpServer? = null
    private var asked: String? = null
    private var path: String? = null
    private var key: String? = null
    private var calls = 0

    private fun answering(
        status: Int,
        body: String,
        apiKey: String = "exa_test_key",
    ): ExaWebSearchGateway {
        val started =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    calls++
                    path = exchange.requestMethod + " " + exchange.requestURI.path
                    key = exchange.requestHeaders.getFirst("x-api-key")
                    asked = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                    val bytes = body.toByteArray()
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
        server = started
        return ExaWebSearchGateway(baseUrl = "http://127.0.0.1:${started.address.port}", apiKey = apiKey)
    }

    private fun assertFails(
        kind: WebSearchFailed.Kind,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WebSearchFailed::class.java) {
            assertThat(it.kind).isEqualTo(kind)
        }
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `a search asks for five pages with short excerpts, and reads each page and the cost`() {
        val gateway =
            answering(
                200,
                """
                {"requestId": "req-1", "costDollars": {"total": 0.007, "search": {"neural": 0.007}},
                 "results": [
                   {"title": "운영체제 교재 비교", "url": "https://example.com/os", "publishedDate": "2026-01-02T00:00:00.000Z",
                    "highlights": ["공룡책은 전공 교재다", " "]},
                   {"url": "https://blog.example.org/post"},
                   {"title": "no web page", "url": "ftp://example.com/file"}
                 ]}
                """.trimIndent(),
            )

        val results = gateway.search("운영체제 교재 추천")

        assertThat(path).isEqualTo("POST /search")
        assertThat(key).isEqualTo("exa_test_key")
        val request = JsonMapper().readTree(asked)
        assertThat(request.path("query").asString()).isEqualTo("운영체제 교재 추천")
        assertThat(request.path("type").asString()).isEqualTo("auto")
        assertThat(request.path("numResults").asInt()).isEqualTo(5)
        assertThat(
            request
                .path("contents")
                .path("highlights")
                .path("maxCharacters")
                .asInt(),
        ).isEqualTo(800)
        assertThat(results.requestId).isEqualTo("req-1")
        assertThat(results.costMicros).isEqualTo(7_000)
        assertThat(
            results.pages.map { it.url },
        ).containsExactly("https://example.com/os", "https://blog.example.org/post")
        assertThat(results.pages.first().published).isEqualTo("2026-01-02")
        assertThat(results.pages.first().highlights).containsExactly("공룡책은 전공 교재다")
        assertThat(results.pages.last().title).isEqualTo("blog.example.org")
    }

    @Test
    fun `without a key nothing is asked`() {
        val gateway = answering(200, """{"results": []}""", apiKey = "")

        assertThat(gateway.available).isFalse()
        assertFails(WebSearchFailed.Kind.UNAVAILABLE) { gateway.search("운영체제") }
        assertThat(calls).isZero()
    }

    @Test
    fun `a refused key, no credit, the rate limit and an answer that cannot be read each fail as they are`() {
        assertFails(WebSearchFailed.Kind.UNAVAILABLE) { answering(401, "{}").search("a") }
        stop()
        assertFails(WebSearchFailed.Kind.UNAVAILABLE) { answering(402, "{}").search("a") }
        stop()
        assertFails(WebSearchFailed.Kind.BUSY) { answering(429, "{}").search("a") }
        stop()
        assertFails(WebSearchFailed.Kind.FAILED) { answering(500, "{}").search("a") }
        stop()
        assertFails(WebSearchFailed.Kind.FAILED) { answering(200, "<html>").search("a") }
        stop()
        assertFails(WebSearchFailed.Kind.FAILED) { answering(200, """{"requestId": "r"}""").search("a") }
    }

    @Test
    fun `an answer without a cost is still read`() {
        val results = answering(200, """{"results": [{"title": "t", "url": "https://example.com"}]}""").search("a")

        assertThat(results.costMicros).isNull()
        assertThat(results.requestId).isNull()
        assertThat(results.pages).hasSize(1)
    }
}
