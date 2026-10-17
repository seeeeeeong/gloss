package gloss.domain.setup.infrastructure

import gloss.domain.setup.domain.FetchedPage
import gloss.domain.setup.domain.PageAddressPolicy
import gloss.domain.setup.domain.PageFetchFailed
import gloss.domain.setup.domain.PageGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Reads a public web page over HTTPS for the model, guarded against server-side request forgery: the
 * host's every address must be public, the connection goes to the address that was checked (never
 * resolved again, so a second answer cannot point it inward), and each redirect is checked as the
 * first address was. Bounded in size, time and redirects; sends no cookie and no credential.
 */
@Component
class HttpPageGateway internal constructor(
    private val clock: Clock,
    private val resolver: Resolver,
    private val connector: Connector,
    private val resolveMillis: Long,
) : PageGateway {
    @Autowired
    constructor(clock: Clock) : this(
        clock = clock,
        resolver = SYSTEM_RESOLVER,
        connector = TLS_CONNECTOR,
        resolveMillis = RESOLVE_TIMEOUT.toMillis(),
    )

    private val log = LoggerFactory.getLogger(javaClass)

    /** A host's addresses, as DNS gives them. */
    fun interface Resolver {
        fun resolve(host: String): List<InetAddress>
    }

    /** A connection to [address], speaking for [host] as TLS and HTTP name it. */
    fun interface Connector {
        fun open(
            address: InetAddress,
            host: String,
            timeout: Duration,
        ): Socket
    }

    override fun fetch(url: String): FetchedPage {
        val deadline = clock.instant().plus(WHOLE_TIMEOUT)
        var target = checked(url)
        repeat(MAX_REDIRECTS + 1) {
            val response = request(target, deadline)
            val location = response.location
            if (location == null) return pageOf(target, response, deadline)
            target = checked(location)
        }
        throw PageFetchFailed(PageFetchFailed.Kind.TOO_MANY_REDIRECTS)
    }

    private fun checked(url: String): URI {
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                throw PageFetchFailed(PageFetchFailed.Kind.NOT_ALLOWED)
            }
        val allowed =
            url.length <= PageAddressPolicy.MAX_URL &&
                uri.rawUserInfo == null &&
                PageAddressPolicy.allows(uri.scheme, uri.host, uri.port)
        if (!allowed) throw PageFetchFailed(PageFetchFailed.Kind.NOT_ALLOWED)
        // The scheme as source_page keeps it: the policy takes HTTPS in any case.
        return URI(PageAddressPolicy.SCHEME + url.substring(uri.scheme.length))
    }

    // Every address the host has must be public: one that also answers inward is refused, not tried.
    // Looked up apart from the caller, so a name server that never answers holds the fetch no longer
    // than the time it has left.
    private fun addressOf(
        host: String,
        deadline: Instant,
    ): InetAddress {
        val name = host.removePrefix("[").removeSuffix("]")
        val lookup = CompletableFuture.supplyAsync({ resolver.resolve(name) }, LOOKUPS)
        val addresses =
            try {
                lookup.get(waitFor(deadline, Duration.ofMillis(resolveMillis)).toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                lookup.cancel(true)
                failed()
            } catch (_: ExecutionException) {
                failed()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                failed()
            }
        checkTime(deadline)
        if (addresses.isEmpty() || addresses.any { !PageAddressPolicy.isPublic(it.address) }) {
            throw PageFetchFailed(PageFetchFailed.Kind.NOT_ALLOWED)
        }
        return addresses.first()
    }

    private class Response(
        val headers: Map<String, String>,
        val body: ByteArray,
        val location: String?,
    )

    private fun request(
        target: URI,
        deadline: Instant,
    ): Response {
        val host = target.host
        val address = addressOf(host, deadline)
        try {
            connector.open(address, host, waitFor(deadline, CONNECT_TIMEOUT)).use { socket ->
                socket.soTimeout = READ_TIMEOUT.toMillis().toInt()
                socket.getOutputStream().apply {
                    write(requestOf(target))
                    flush()
                }
                val input = socket.getInputStream().buffered()
                val (status, headers) = headOf(input, deadline)
                if (status in REDIRECTS) {
                    val location = headers["location"] ?: failed()
                    return Response(headers, ByteArray(0), target.resolve(location.trim()).toString())
                }
                if (status != OK) failed()
                checkReadable(headers)
                return Response(headers, bodyOf(input, headers, deadline), location = null)
            }
        } catch (e: IOException) {
            log.info("A page could not be read: {}", e.javaClass.simpleName)
            failed()
        } catch (_: IllegalArgumentException) {
            failed()
        }
    }

    private fun headOf(
        input: InputStream,
        deadline: Instant,
    ): Pair<Int, Map<String, String>> {
        val budget = intArrayOf(MAX_HEAD_BYTES)
        val statusLine = lineOf(input, deadline, budget) ?: failed()
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: failed()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = lineOf(input, deadline, budget) ?: failed()
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return status to headers
    }

    // A line of the head, its CRLF dropped; null at the end of the stream. [budget] is what is left.
    private fun lineOf(
        input: InputStream,
        deadline: Instant,
        budget: IntArray,
    ): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            checkTime(deadline)
            val byte = input.read()
            if (byte < 0) return if (line.size() == 0) null else line.toString(Charsets.ISO_8859_1)
            if (--budget[0] < 0) failed()
            if (byte == '\n'.code) return line.toString(Charsets.ISO_8859_1).removeSuffix("\r")
            line.write(byte)
        }
    }

    private fun checkReadable(headers: Map<String, String>) {
        val type = headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
        val encoding = headers["content-encoding"]?.trim()?.lowercase()
        val readable = (type == null || type in TEXT_TYPES) && (encoding == null || encoding == "identity")
        if (!readable) failed(PageFetchFailed.Kind.NOT_TEXT)
        val length = headers["content-length"]?.trim()?.toLongOrNull()
        if (length != null && length > MAX_BYTES) failed(PageFetchFailed.Kind.TOO_LARGE)
    }

    private fun bodyOf(
        input: InputStream,
        headers: Map<String, String>,
        deadline: Instant,
    ): ByteArray {
        val body = Body(input, deadline)
        if (headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true) {
            body.readChunks()
        } else {
            body.readAll(headers["content-length"]?.trim()?.toLongOrNull())
        }
        return body.bytes()
    }

    private inner class Body(
        private val input: InputStream,
        private val deadline: Instant,
    ) {
        private val out = ByteArrayOutputStream()
        private val buffer = ByteArray(BUFFER_BYTES)

        fun bytes(): ByteArray = out.toByteArray()

        fun readAll(length: Long?) {
            var left = length ?: Long.MAX_VALUE
            while (left > 0) {
                val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                if (read < 0) break
                keep(read)
                left -= read
            }
        }

        // Each size line has a bound of its own: MAX_BYTES bounds the body, so a page sent in many
        // small chunks reads as well as one sent in a few.
        fun readChunks() {
            while (true) {
                val sizeLine = lineOf(input, deadline, intArrayOf(MAX_CHUNK_LINE_BYTES)) ?: failed()
                val size = sizeLine.substringBefore(';').trim().toLongOrNull(HEX) ?: failed()
                if (size == 0L) return
                if (out.size() + size > MAX_BYTES) failed(PageFetchFailed.Kind.TOO_LARGE)
                var left = size
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (read < 0) failed()
                    keep(read)
                    left -= read
                }
                lineOf(input, deadline, intArrayOf(MAX_CHUNK_LINE_BYTES))
            }
        }

        private fun keep(read: Int) {
            checkTime(deadline)
            if (out.size() + read > MAX_BYTES) failed(PageFetchFailed.Kind.TOO_LARGE)
            out.write(buffer, 0, read)
        }
    }

    // A page that trickles in a byte at a time still ends here, however slow each read is.
    private fun checkTime(deadline: Instant) {
        if (clock.instant().isAfter(deadline)) failed()
    }

    // At most [most], and never past the fetch's own deadline; a millisecond at least, as 0 waits forever.
    private fun waitFor(
        deadline: Instant,
        most: Duration,
    ): Duration = minOf(most, Duration.between(clock.instant(), deadline)).coerceAtLeast(Duration.ofMillis(1))

    private fun failed(kind: PageFetchFailed.Kind = PageFetchFailed.Kind.FAILED): Nothing = throw PageFetchFailed(kind)

    // Parsed only once the body is within MAX_BYTES, and let go of if parsing ran past the deadline.
    private fun pageOf(
        target: URI,
        response: Response,
        deadline: Instant,
    ): FetchedPage {
        val type = response.headers["content-type"]?.lowercase().orEmpty()
        val decoded = String(response.body, charsetOf(type, response.body))
        val page =
            if (type.startsWith("text/plain")) {
                FetchedPage(url = target.toString(), title = null, text = HtmlText.plain(decoded))
            } else {
                val html = HtmlText.Page(decoded)
                FetchedPage(url = target.toString(), title = html.title, text = html.text)
            }
        checkTime(deadline)
        return page
    }

    internal companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

        // A name server answers in milliseconds; one that has not in five seconds will not in time.
        val RESOLVE_TIMEOUT: Duration = Duration.ofSeconds(5)

        // A thread each, cheap while it waits; the fetches at once are held to the turns at once.
        private val LOOKUPS = Executors.newVirtualThreadPerTaskExecutor()

        // A stalled server is let go of quickly; the model's turn waits on this.
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
        val WHOLE_TIMEOUT: Duration = Duration.ofSeconds(15)

        // A long article with its markup is under 1 MiB; more is not a contents page.
        const val MAX_BYTES = 2L * 1024 * 1024

        // A moved page and its canonical address take one or two; more is a loop or a tracker, and each
        // hop is one more address to check inside the same 15 seconds.
        const val MAX_REDIRECTS = 3
        private const val MAX_HEAD_BYTES = 32 * 1024

        // A size in hex and its extensions; longer is no chunk's size.
        private const val MAX_CHUNK_LINE_BYTES = 1024
        private const val BUFFER_BYTES = 16 * 1024
        const val SNIFF_BYTES = 4 * 1024
        private const val HEX = 16
        private const val OK = 200
        const val USER_AGENT = "Gloss/1 (contents check)"
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val TEXT_TYPES = setOf("text/html", "application/xhtml+xml", "text/plain")
        val CHARSET = Regex("""(?i)charset\s*=\s*["']?([A-Za-z0-9._:-]+)""")

        val SYSTEM_RESOLVER = Resolver { host -> InetAddress.getAllByName(host).toList() }

        // The certificate is checked against the host, over a socket to the address already checked.
        val TLS_CONNECTOR =
            Connector { address, host, timeout ->
                val plain = Socket()
                try {
                    plain.connect(InetSocketAddress(address, PageAddressPolicy.PORT), timeout.toMillis().toInt())
                    plain.soTimeout = READ_TIMEOUT.toMillis().toInt()
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory

                    // A Java method's parameters have no names to give.
                    @Suppress("NamedArguments")
                    val tls = factory.createSocket(plain, host, PageAddressPolicy.PORT, true) as SSLSocket
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    tls.startHandshake()
                    tls
                } catch (e: IOException) {
                    plain.close()
                    throw e
                }
            }
    }
}

private fun requestOf(target: URI): ByteArray {
    val path = (target.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + target.rawQuery?.let { "?$it" }.orEmpty()
    return (
        "GET $path HTTP/1.1\r\n" +
            "Host: ${target.host}\r\n" +
            "User-Agent: ${HttpPageGateway.USER_AGENT}\r\n" +
            "Accept: text/html,application/xhtml+xml,text/plain;q=0.9\r\n" +
            "Accept-Encoding: identity\r\n" +
            "Connection: close\r\n\r\n"
    ).toByteArray(Charsets.US_ASCII)
}

// As the header names it, or the page's own meta tag, or UTF-8.
private fun charsetOf(
    type: String,
    body: ByteArray,
): Charset {
    val named =
        HttpPageGateway.CHARSET
            .find(type)
            ?.groupValues
            ?.get(1)
            ?: HttpPageGateway.CHARSET
                .find(String(body.copyOf(minOf(body.size, HttpPageGateway.SNIFF_BYTES)), Charsets.ISO_8859_1))
                ?.groupValues
                ?.get(1)
    return try {
        named?.let { Charset.forName(it.trim('"', '\'')) } ?: Charsets.UTF_8
    } catch (_: IllegalCharsetNameException) {
        Charsets.UTF_8
    } catch (_: UnsupportedCharsetException) {
        Charsets.UTF_8
    }
}
