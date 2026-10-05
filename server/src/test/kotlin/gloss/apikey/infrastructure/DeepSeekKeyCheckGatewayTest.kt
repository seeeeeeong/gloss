package gloss.apikey.infrastructure

import com.sun.net.httpserver.HttpServer
import gloss.apikey.domain.ApiKeySecret
import gloss.apikey.domain.KeyCheckResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class DeepSeekKeyCheckGatewayTest {
    private val secret = checkNotNull(ApiKeySecret.of("sk-0123456789abcdef"))
    private var server: HttpServer? = null
    private var seen: Pair<String, String?>? = null

    private fun answering(status: Int): DeepSeekKeyCheckGateway {
        val started =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    seen = exchange.requestURI.path to exchange.requestHeaders.getFirst("Authorization")
                    val body = """{"is_available": true, "balance_infos": []}""".toByteArray()
                    exchange.sendResponseHeaders(status, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }
        server = started
        return DeepSeekKeyCheckGateway(baseUrl = "http://127.0.0.1:${started.address.port}")
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `a key DeepSeek answers for is valid, asked through the balance endpoint`() {
        assertThat(answering(200).check(secret)).isEqualTo(KeyCheckResult.Valid)
        assertThat(seen).isEqualTo("/user/balance" to "Bearer sk-0123456789abcdef")
    }

    @Test
    fun `an authentication failure means the key is rejected`() {
        assertThat(answering(401).check(secret)).isEqualTo(KeyCheckResult.Rejected)
    }

    @Test
    fun `a rate limit or a server error says nothing about the key`() {
        assertThat(answering(429).check(secret)).isEqualTo(KeyCheckResult.Unavailable)
        stop()
        assertThat(answering(503).check(secret)).isEqualTo(KeyCheckResult.Unavailable)
    }

    @Test
    fun `no answer at all says nothing about the key`() {
        val gateway = answering(200)
        stop()

        assertThat(gateway.check(secret)).isEqualTo(KeyCheckResult.Unavailable)
    }
}
