package gloss.domain.setup.infrastructure

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gloss.MutableClock
import gloss.domain.setup.domain.PageFetchFailed
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch

// The server speaks plain HTTP on loopback; the connector stands in for TLS and the resolver for DNS,
// so every address the gateway judges is the one a test names.
class HttpPageGatewayTest {
    private val clock = MutableClock()
    private var server: HttpServer? = null
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val connected: MutableList<InetAddress> = Collections.synchronizedList(mutableListOf())
    private val hosts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val addresses =
        mapOf(
            "books.example" to listOf(address(93, 184, 216, 34)),
            "inside.example" to listOf(address(10, 0, 0, 5)),
            "metadata.example" to listOf(address(169, 254, 169, 254)),
            "mixed.example" to listOf(address(93, 184, 216, 34), address(127, 0, 0, 1)),
        )

    private fun address(vararg bytes: Int): InetAddress =
        InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray())

    private fun gateway(
        handle: (HttpExchange) -> Unit,
        whenConnected: () -> Unit = {},
        whenResolved: (String) -> Unit = {},
    ): HttpPageGateway {
        val started =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    requests += exchange.requestURI.toString()
                    hosts += exchange.requestHeaders.getFirst("Host").orEmpty()
                    try {
                        handle(exchange)
                    } finally {
                        exchange.close()
                    }
                }
                start()
            }
        server = started
        val resolver =
            HttpPageGateway.Resolver { host ->
                whenResolved(host)
                addresses[host] ?: InetAddress.getAllByName(host).toList()
            }
        val connector =
            HttpPageGateway.Connector { address, _, _ ->
                connected += address
                whenConnected()
                Socket("127.0.0.1", started.address.port)
            }
        return HttpPageGateway(
            clock = clock,
            resolver = resolver,
            connector = connector,
            resolveMillis = RESOLVE_WAIT_MILLIS,
        )
    }

    private fun HttpExchange.answer(
        status: Int,
        body: String,
        type: String = "text/html; charset=utf-8",
        chunked: Boolean = false,
    ) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, if (chunked) 0 else bytes.size.toLong())
        responseBody.write(bytes)
    }

    private fun HttpExchange.redirect(to: String) {
        responseHeaders.add("Location", to)
        sendResponseHeaders(302, -1)
    }

    private fun assertFails(
        kind: PageFetchFailed.Kind,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(PageFetchFailed::class.java) {
            assertThat(it.kind).isEqualTo(kind)
        }
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `a public page is read as text, line by line, with its title, from the address that was checked`() {
        val html =
            """
            <html><head><title>운영체제 10판 목차 &amp; 정리</title><style>p { color: red }</style></head>
            <body><script>alert('x')</script><h1>목차</h1>
            <ul><li>1.1 운영체제가&nbsp;할 일</li><li>1.2 컴퓨터 시스템의 구성 &#8212; 개요</li></ul></body></html>
            """.trimIndent()
        val gateway = gateway({ it.answer(200, html, chunked = true) })

        val page = gateway.fetch("https://books.example/os/toc?ed=10")

        assertThat(page.url).isEqualTo("https://books.example/os/toc?ed=10")
        assertThat(page.title).isEqualTo("운영체제 10판 목차 & 정리")
        assertThat(page.text.lines()).containsExactly("목차", "1.1 운영체제가 할 일", "1.2 컴퓨터 시스템의 구성 — 개요")
        assertThat(requests).containsExactly("/os/toc?ed=10")
        assertThat(hosts).containsExactly("books.example")
        assertThat(connected).containsExactly(address(93, 184, 216, 34))
    }

    @Test
    fun `a page in another charset is read as its header names it, and plain text as it is`() {
        val gateway =
            gateway({ exchange ->
                val bytes = "1장 서론\n\n  1.1 개요".toByteArray(charset("EUC-KR"))
                exchange.responseHeaders.add("Content-Type", "text/plain; charset=EUC-KR")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            })

        val page = gateway.fetch("https://books.example/toc.txt")

        assertThat(page.title).isNull()
        assertThat(page.text).isEqualTo("1장 서론\n1.1 개요")
    }

    @Test
    fun `an address not over https, on another port or with credentials is refused before anything is asked`() {
        val gateway = gateway({ it.answer(200, "<p>목차</p>") })

        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("http://books.example/toc") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://books.example:8443/toc") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://user:pass@books.example/toc") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("file:///etc/passwd") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://books.example/" + "a".repeat(2_000)) }
        assertThat(connected).isEmpty()
    }

    @Test
    fun `a host that resolves inside the network, even in part, is never connected to`() {
        val gateway = gateway({ it.answer(200, "<p>목차</p>") })

        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://inside.example/") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://metadata.example/latest/meta-data") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://mixed.example/") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://127.0.0.1/") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://169.254.169.254/latest/meta-data") }
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { gateway.fetch("https://[::1]/") }
        assertThat(connected).isEmpty()
    }

    @Test
    fun `a redirect is checked as the first address was, and one inward or off https is refused`() {
        val inward = gateway({ it.redirect("https://inside.example/admin") })
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { inward.fetch("https://books.example/toc") }
        stop()
        val plain = gateway({ it.redirect("http://books.example/toc") })
        assertFails(PageFetchFailed.Kind.NOT_ALLOWED) { plain.fetch("https://books.example/toc") }

        assertThat(connected).containsExactly(address(93, 184, 216, 34), address(93, 184, 216, 34))
    }

    @Test
    fun `a redirect to a public page is followed, relative to the page, three times at most`() {
        val once =
            gateway({ exchange ->
                if (exchange.requestURI.path == "/old") exchange.redirect("/new") else exchange.answer(200, "<p>목차</p>")
            })
        val page = once.fetch("https://books.example/old")
        assertThat(page.url).isEqualTo("https://books.example/new")
        assertThat(page.text).isEqualTo("목차")
        stop()

        val looping = gateway({ it.redirect("https://books.example/again") })
        assertFails(PageFetchFailed.Kind.TOO_MANY_REDIRECTS) { looping.fetch("https://books.example/start") }
        assertThat(requests.drop(2)).hasSize(4)
    }

    @Test
    fun `a page sent in many small chunks is read whole`() {
        val gateway =
            gateway({ exchange ->
                exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
                exchange.sendResponseHeaders(200, 0)
                // Each flush is one chunk: about 50 KB of framing, past any budget shared by the body.
                repeat(10_000) {
                    exchange.responseBody.write("가".toByteArray())
                    exchange.responseBody.flush()
                }
            })

        val page = gateway.fetch("https://books.example/streamed")

        assertThat(page.text).hasSize(10_000)
    }

    @Test
    fun `a page larger than 2 MiB is refused, whether its length is told or it keeps coming`() {
        val big = "가".repeat(800_000)
        val told = gateway({ it.answer(200, big) })
        assertFails(PageFetchFailed.Kind.TOO_LARGE) { told.fetch("https://books.example/big") }
        stop()

        val streamed = gateway({ it.answer(200, big, chunked = true) })
        assertFails(PageFetchFailed.Kind.TOO_LARGE) { streamed.fetch("https://books.example/big") }
    }

    @Test
    fun `a page that is not text, or not found, gives nothing to read`() {
        val pdf = gateway({ it.answer(200, "%PDF-1.7", type = "application/pdf") })
        assertFails(PageFetchFailed.Kind.NOT_TEXT) { pdf.fetch("https://books.example/toc.pdf") }
        stop()

        val missing = gateway({ it.answer(404, "<p>없음</p>") })
        assertFails(PageFetchFailed.Kind.FAILED) { missing.fetch("https://books.example/gone") }
    }

    @Test
    fun `an address in any case of https is read, and named in lowercase`() {
        val gateway = gateway({ it.answer(200, "<p>목차</p>") })

        val page = gateway.fetch("HTTPS://books.example/toc")

        assertThat(page.url).isEqualTo("https://books.example/toc")
    }

    @Test
    fun `a name server that never answers, or answers past the whole time, is let go of without a connection`() {
        val never = CountDownLatch(1)
        val hung = gateway({ it.answer(200, "<p>목차</p>") }, whenResolved = { never.await() })
        assertFails(PageFetchFailed.Kind.FAILED) { hung.fetch("https://books.example/toc") }
        never.countDown()
        stop()

        val late =
            gateway(
                { it.answer(200, "<p>목차</p>") },
                whenResolved = { clock.advance(Duration.ofSeconds(16)) },
            )
        assertFails(PageFetchFailed.Kind.FAILED) { late.fetch("https://books.example/toc") }
        assertThat(connected).isEmpty()
    }

    @Test
    fun `a page that takes longer than its whole time is let go of`() {
        val slow = gateway({ it.answer(200, "<p>목차</p>") }, whenConnected = { clock.advance(Duration.ofSeconds(16)) })

        assertFails(PageFetchFailed.Kind.FAILED) { slow.fetch("https://books.example/slow") }
    }

    private companion object {
        // Stands for the five seconds a lookup is given, so a test waits on a lookup that never ends briefly.
        const val RESOLVE_WAIT_MILLIS = 100L
    }
}
