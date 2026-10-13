package gloss.book.infrastructure

import gloss.book.domain.BookSource
import gloss.book.domain.BookSourceFailed
import gloss.book.domain.BookSourceGateway
import gloss.book.domain.Isbn13
import gloss.book.domain.SourcedBook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * YES24's Open API (developers.yes24.com), on the operator's key: the basic tier allows 20,000 calls
 * a day and 10 a second. A search and a book each take one call, since `detail=Y` carries the contents
 * with the rest. Its terms ask that YES24 be named and the book's page linked wherever these details
 * show, and forbid collecting them into a database of our own: a book is fetched when a reader adds
 * it, never ahead. Never logs the key.
 */
@Component
class Yes24BookSourceGateway(
    @Value("\${gloss.yes24.base-url}") baseUrl: String,
    @Value("\${gloss.yes24.api-key}") private val apiKey: String,
) : BookSourceGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = JsonMapper()

    // The reader waits on the search while this runs: a short wait, then "try again".
    private val restClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(READ_TIMEOUT) },
            ).build()

    // Every category, since a translated book is BOOK and its original FOREIGN.
    override fun search(query: String): List<SourcedBook> =
        items("/goods/itemList?query={query}&category=ALL&sort=RELATION&pageSize={size}&detail=Y", query, PAGE_SIZE)
            .mapNotNull(::bookOf)

    override fun find(ref: String): SourcedBook? {
        val id = ref.takeIf(ITEM_ID::matches)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return items("/goods/itemDetail?searchType=ItemId&query={id}&detail=Y", id).firstNotNullOfOrNull(::bookOf)
    }

    private fun items(
        uri: String,
        vararg values: Any,
    ): List<JsonNode> {
        if (apiKey.isBlank()) throw BookSourceFailed(BookSourceFailed.Kind.UNAVAILABLE)
        try {
            return restClient
                .get()
                .uri(uri, *values)
                .header(KEY_HEADER, apiKey)
                .exchange { _, response -> itemsOf(response.statusCode.value(), response.body.readAllBytes()) }
        } catch (e: ResourceAccessException) {
            log.warn("YES24 gave no answer: {}", e.javaClass.simpleName)
            throw BookSourceFailed(BookSourceFailed.Kind.FAILED)
        }
    }

    private fun itemsOf(
        status: Int,
        body: ByteArray,
    ): List<JsonNode> {
        val answer =
            try {
                json.readTree(body)
            } catch (e: JacksonException) {
                log.warn("YES24 answered {} with a body that is not JSON: {}", status, e.javaClass.simpleName)
                throw BookSourceFailed(BookSourceFailed.Kind.FAILED)
            }
        val code =
            answer
                .path("errorCode")
                .takeIf { it.isString }
                ?.asString()
                .orEmpty()
        val found = answer.path("data").path("items")
        return when {
            // A 200 that is not the answer the API documents, as a proxy's own page would be, found nothing
            // and says nothing: not "no results".
            status == OK && answer.path("success").asBoolean(false) && found.isArray -> found.toList()

            // "No results" and "no such item" come as 404 with a code; a 404 without one is a wrong address.
            status == NOT_FOUND && code in NOTHING_FOUND -> emptyList()

            else -> throw BookSourceFailed(kindOf(status, code))
        }
    }

    private fun kindOf(
        status: Int,
        code: String,
    ): BookSourceFailed.Kind =
        when (status) {
            UNAUTHORIZED, FORBIDDEN -> {
                log.warn("YES24 refused the key: {} {}", status, code)
                BookSourceFailed.Kind.UNAVAILABLE
            }

            TOO_MANY_REQUESTS -> {
                log.warn("YES24's limit for the key is used up: {}", code)
                BookSourceFailed.Kind.BUSY
            }

            else -> {
                log.warn("YES24 answered {} {}", status, code)
                BookSourceFailed.Kind.FAILED
            }
        }

    // Null for an item with no number, title or ISBN: nothing could be added from it, and without an
    // ISBN it is a set, a record or goods, which a search across every category also finds.
    private fun bookOf(item: JsonNode): SourcedBook? {
        val id = item.path("itemId").takeIf { it.isIntegralNumber && it.canConvertToLong() }?.asLong() ?: return null
        val title = item.text("title")?.let { clip(it, MAX_TITLE) } ?: return null
        val isbn13 = item.text("isbn13")?.let(Isbn13::of) ?: return null
        return SourcedBook(
            source = BookSource.YES24,
            ref = id.toString(),
            url = "$PRODUCT_PAGE$id",
            title = title,
            // One line with roles, as "A,B 공저/C 역": cut at the role groups only, since a comma also
            // sits inside "Kurose, James".
            authors =
                item
                    .text("author")
                    .orEmpty()
                    .split('/')
                    .mapNotNull { clip(it, MAX_NAME) }
                    .take(MAX_AUTHORS),
            publisher = item.text("publisher")?.let { clip(it, MAX_NAME) },
            isbn13 = isbn13,
            publishedOn = item.text("publishDate")?.let(::dateOf),
            kind = item.text("goodsType")?.let { clip(it, MAX_KIND) },
            contents = item.path("contentDetail").text("tableOfContents")?.let(Yes24Contents::clean),
        )
    }

    private fun JsonNode.text(name: String): String? =
        path(name)
            .takeIf {
                it.isString
            }?.asString()
            ?.takeIf { it.isNotBlank() }

    // Null when nothing is left. Counted by code point, as PostgreSQL's char_length does.
    private fun clip(
        text: String,
        max: Int,
    ): String? {
        val clean = text.filterNot { it.isISOControl() }.trim()
        val end = clean.offsetByCodePoints(0, minOf(max, clean.codePointCount(0, clean.length)))
        return clean.substring(0, end).trim().takeIf { it.isNotEmpty() }
    }

    // "20200910" is what the API sends and "2013-12-24" what its documentation shows.
    private fun dateOf(text: String): LocalDate? =
        DATE_FORMATS.firstNotNullOfOrNull {
            try {
                LocalDate.parse(text, it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    private companion object {
        // A TCP and TLS handshake with a healthy API takes well under a second; three allows a slow link.
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)

        // A search carries ten books' contents, so it gets longer than a key check: eight is as long as
        // a reader should wait before "try again".
        val READ_TIMEOUT: Duration = Duration.ofSeconds(8)
        const val KEY_HEADER = "X-Api-Key"

        // Editions to choose from, each with its contents: enough to find a book by its title.
        const val PAGE_SIZE = 10
        const val PRODUCT_PAGE = "https://www.yes24.com/product/goods/"
        val DATE_FORMATS = listOf(DateTimeFormatter.BASIC_ISO_DATE, DateTimeFormatter.ISO_LOCAL_DATE)

        // YES24 answers a number past a 32-bit int as a bad query (400 PARAM_004), not as no such item.
        val ITEM_ID = Regex("[0-9]{1,10}")

        // No results, no such item, no such ISBN. SEARCH_002 is not among them: it is the search engine
        // failing, which the documentation says to wait out.
        val NOTHING_FOUND = setOf("SEARCH_001", "GOODS_001", "GOODS_002")

        // The lengths a book keeps: its title, a name with its role, and a short word for its kind.
        const val MAX_TITLE = 300
        const val MAX_NAME = 100
        const val MAX_AUTHORS = 20
        const val MAX_KIND = 20
        const val OK = 200
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val TOO_MANY_REQUESTS = 429
    }
}
