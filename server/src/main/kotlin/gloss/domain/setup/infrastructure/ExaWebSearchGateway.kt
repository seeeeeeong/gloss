package gloss.domain.setup.infrastructure

import gloss.domain.setup.domain.WebPage
import gloss.domain.setup.domain.WebResults
import gloss.domain.setup.domain.WebSearchFailed
import gloss.domain.setup.domain.WebSearchGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.time.Duration

/**
 * Exa's search (POST /search, exa.ai/docs/search/quickstart), on the operator's key: $7 per thousand
 * auto searches of up to ten results, 10 a second for the whole team. Each answer reports its own cost
 * estimate in costDollars.total. Never logs the key.
 */
@Component
class ExaWebSearchGateway(
    @Value("\${gloss.exa.base-url}") baseUrl: String,
    @Value("\${gloss.exa.api-key}") private val apiKey: String,
) : WebSearchGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = JsonMapper()

    private val restClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    override val available: Boolean = apiKey.isNotBlank()

    override fun search(query: String): WebResults {
        if (!available) throw WebSearchFailed(WebSearchFailed.Kind.UNAVAILABLE)
        val body = json.writeValueAsString(requestOf(query))
        try {
            return restClient
                .post()
                .uri("/search")
                .header(KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange { _, response -> resultsOf(response.statusCode.value(), response.body.readAllBytes()) }
        } catch (e: ResourceAccessException) {
            log.warn("Exa gave no answer: {}", e.javaClass.simpleName)
            throw WebSearchFailed(WebSearchFailed.Kind.FAILED)
        }
    }

    // The model writes the query alone: the type, the count and the excerpt length are ours, so a
    // search costs the same whatever it asks.
    private fun requestOf(query: String): Map<String, Any> =
        linkedMapOf(
            "query" to query,
            "type" to "auto",
            "numResults" to RESULTS,
            "contents" to mapOf("highlights" to mapOf("maxCharacters" to HIGHLIGHT_CHARACTERS)),
        )

    private fun resultsOf(
        status: Int,
        body: ByteArray,
    ): WebResults {
        if (status != OK) throw WebSearchFailed(kindOf(status))
        val answer = readOrNull(body)
        val results = answer?.path("results")
        if (results == null || !results.isArray) {
            log.warn("Exa answered without results")
            throw WebSearchFailed(WebSearchFailed.Kind.FAILED)
        }
        return WebResults(
            requestId = answer.text("requestId")?.take(MAX_REQUEST_ID),
            costMicros = costOf(answer.path("costDollars").path("total")),
            pages = results.mapNotNull(::pageOf).take(RESULTS),
        )
    }

    private fun readOrNull(body: ByteArray): JsonNode? =
        try {
            json.readTree(body)
        } catch (e: JacksonException) {
            log.warn("Exa answered with a body that is not JSON: {}", e.javaClass.simpleName)
            null
        }

    private fun kindOf(status: Int): WebSearchFailed.Kind =
        when (status) {
            // 402 is a team out of credit or past its spending budget.
            UNAUTHORIZED, PAYMENT_REQUIRED, FORBIDDEN -> {
                log.warn("Exa refused the key: {}", status)
                WebSearchFailed.Kind.UNAVAILABLE
            }

            TOO_MANY_REQUESTS -> {
                log.warn("Exa's rate limit was reached")
                WebSearchFailed.Kind.BUSY
            }

            else -> {
                log.warn("Exa answered {}", status)
                WebSearchFailed.Kind.FAILED
            }
        }

    // Only a web page a reader can open from a link: an address of another scheme is dropped.
    private fun pageOf(result: JsonNode): WebPage? {
        val url = result.text("url")?.takeIf(::isWebAddress) ?: return null
        val highlights =
            result
                .path("highlights")
                .filter { it.isString }
                .map { it.asString().trim() }
                .filter { it.isNotEmpty() }
        return WebPage(
            url = url,
            title = result.text("title")?.trim()?.take(MAX_TITLE) ?: hostOf(url),
            published = result.text("publishedDate")?.take(DATE_LENGTH),
            highlights = highlights,
        )
    }

    private fun costOf(total: JsonNode): Long? =
        total
            .takeIf { it.isNumber }
            ?.decimalValue()
            ?.movePointRight(MICROS_DIGITS)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.takeIf { it.signum() >= 0 && it <= BigDecimal(Long.MAX_VALUE) }
            ?.toLong()

    private fun isWebAddress(url: String): Boolean =
        url.length <= MAX_URL &&
            try {
                val uri = URI(url)
                uri.scheme in WEB_SCHEMES && !uri.host.isNullOrEmpty()
            } catch (_: URISyntaxException) {
                false
            }

    private fun hostOf(url: String): String = URI(url).host

    private fun JsonNode.text(name: String): String? =
        path(name)
            .takeIf { it.isString }
            ?.asString()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)

        // An auto search answers in about a second; the model's turn waits on this.
        val READ_TIMEOUT: Duration = Duration.ofSeconds(15)
        const val KEY_HEADER = "x-api-key"

        // Within the ten a base price covers; five pages of excerpts are a few thousand prompt tokens.
        const val RESULTS = 5

        // Per page: enough for a review's verdict on a book, little enough to keep the prompt small.
        const val HIGHLIGHT_CHARACTERS = 800
        const val MICROS_DIGITS = 6
        const val DATE_LENGTH = 10
        const val MAX_TITLE = 300
        const val MAX_URL = 2_000
        const val MAX_REQUEST_ID = 200
        val WEB_SCHEMES = setOf("https", "http")
        const val OK = 200
        const val UNAUTHORIZED = 401
        const val PAYMENT_REQUIRED = 402
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
    }
}
