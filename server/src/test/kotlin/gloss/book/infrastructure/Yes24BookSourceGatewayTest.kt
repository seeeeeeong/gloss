package gloss.book.infrastructure

import com.sun.net.httpserver.HttpServer
import gloss.book.domain.BookSource
import gloss.book.domain.BookSourceFailed
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.LocalDate

class Yes24BookSourceGatewayTest {
    private var server: HttpServer? = null
    private var asked: String? = null
    private var key: String? = null
    private var calls = 0

    private fun answering(
        status: Int,
        body: String,
        apiKey: String = "yk_test_key",
    ): Yes24BookSourceGateway {
        val started =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    calls++
                    asked =
                        exchange.requestURI.path + "?" + URLDecoder.decode(exchange.requestURI.rawQuery, Charsets.UTF_8)
                    key = exchange.requestHeaders.getFirst("X-Api-Key")
                    val bytes = body.toByteArray()
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
        server = started
        return Yes24BookSourceGateway(baseUrl = "http://127.0.0.1:${started.address.port}/v1", apiKey = apiKey)
    }

    private fun item(
        id: Long = 101,
        isbn13: String = "9788966262472",
        toc: String? = "1장 객체<br>1.1 티켓",
        published: String = "20190617",
    ) = """
        {"itemId": $id, "title": " 오브젝트  ", "author": "조영호 저/홍길동,김철수 공역", "goodsType": "도서",
         "publisher": "위키북스", "isbn13": "$isbn13", "publishDate": "$published",
         "link": "https://www.yes24.com/product/goods/$id",
         "contentDetail": {"bookIntroduction": null, "tableOfContents": ${toc?.let { "\"$it\"" } ?: "null"}}}
        """.trimIndent()

    private fun found(vararg items: String) =
        """{"success": true, "errorCode": null, "data": {"items": [${items.joinToString(",")}]}}"""

    private fun refused(code: String) = """{"success": false, "message": "no", "data": null, "errorCode": "$code"}"""

    private fun assertFails(
        kind: BookSourceFailed.Kind,
        call: () -> Unit,
    ) {
        assertThatThrownBy(
            call,
        ).isInstanceOfSatisfying(BookSourceFailed::class.java) { assertThat(it.kind).isEqualTo(kind) }
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `a search asks every category with contents, and reads each book it finds`() {
        val gateway = answering(200, found(item()))

        val book = gateway.search("오브젝트 & 설계").single()

        assertThat(
            asked,
        ).isEqualTo("/v1/goods/itemList?query=오브젝트 & 설계&category=ALL&sort=RELATION&pageSize=10&detail=Y")
        assertThat(key).isEqualTo("yk_test_key")
        assertThat(book.source).isEqualTo(BookSource.YES24)
        assertThat(book.ref).isEqualTo("101")
        assertThat(book.url).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(book.title).isEqualTo("오브젝트")
        assertThat(book.authors).containsExactly("조영호 저", "홍길동,김철수 공역")
        assertThat(book.publisher).isEqualTo("위키북스")
        assertThat(book.isbn13.value).isEqualTo("9788966262472")
        assertThat(book.publishedOn).isEqualTo(LocalDate.of(2019, 6, 17))
        assertThat(book.kind).isEqualTo("도서")
        assertThat(book.contents).isEqualTo("1장 객체\n1.1 티켓")
    }

    @Test
    fun `what has no isbn is not a book, whether searched for or asked for by its number`() {
        val gateway = answering(200, found(item(id = 1, isbn13 = ""), item(id = 2, toc = null)))

        val books = gateway.search("세트")

        assertThat(books.map { it.ref }).containsExactly("2")
        assertThat(books.single().contents).isNull()
        stop()
        assertThat(answering(200, found(item(id = 1, isbn13 = ""))).find("1")).isNull()
    }

    @Test
    fun `no results is an empty list, but a search engine that failed is a failure`() {
        assertThat(answering(404, refused("SEARCH_001")).search("없는 책")).isEmpty()
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(404, refused("SEARCH_002")).search("책") }
    }

    @Test
    fun `a date is read as the API sends it and as its documentation shows it, and left out otherwise`() {
        val documented = answering(200, found(item(published = "2013-12-24"))).search("책").single()
        stop()
        val unknown = answering(200, found(item(published = "2013년 12월"))).search("책").single()

        assertThat(documented.publishedOn).isEqualTo(LocalDate.of(2013, 12, 24))
        assertThat(unknown.publishedOn).isNull()
    }

    @Test
    fun `a book is read again by its item number, up to the largest YES24 takes`() {
        val gateway = answering(200, found(item(id = 2147483647)))

        val book = gateway.find("2147483647")

        assertThat(asked).isEqualTo("/v1/goods/itemDetail?searchType=ItemId&query=2147483647&detail=Y")
        assertThat(book?.ref).isEqualTo("2147483647")
    }

    @Test
    fun `a reference that is no item number, or names no item, finds nothing`() {
        val gateway = answering(404, refused("GOODS_001"))

        assertThat(gateway.find("1 or 1=1")).isNull()
        assertThat(gateway.find("2147483648")).isNull()
        assertThat(gateway.find("0")).isNull()
        assertThat(calls).isZero()
        assertThat(gateway.find("999")).isNull()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `without a key nothing is asked`() {
        val gateway = answering(200, found(item()), apiKey = "")

        assertFails(BookSourceFailed.Kind.UNAVAILABLE) { gateway.search("오브젝트") }
        assertThat(calls).isZero()
    }

    @Test
    fun `a refused key, a spent limit and a failure are told apart`() {
        assertFails(BookSourceFailed.Kind.UNAVAILABLE) { answering(401, refused("AUTH_002")).search("책") }
        stop()
        assertFails(BookSourceFailed.Kind.BUSY) { answering(429, refused("RATE_001")).search("책") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(503, refused("UPSTREAM_UNAVAILABLE")).search("책") }
    }

    @Test
    fun `a 404 without a code, a body that is not JSON and no answer are failures`() {
        assertFails(BookSourceFailed.Kind.FAILED) { answering(404, "{}").search("책") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(200, "<html>").search("책") }
        stop()
        val gateway = answering(200, found(item()))
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { gateway.search("책") }
    }

    @Test
    fun `a 200 that is not the documented answer is a failure, not an empty result`() {
        assertFails(BookSourceFailed.Kind.FAILED) { answering(200, "").search("책") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(200, "null").find("101") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(200, "{}").search("책") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) { answering(200, refused("SEARCH_001")).find("101") }
        stop()
        assertFails(BookSourceFailed.Kind.FAILED) {
            answering(200, """{"success": true, "data": {"items": null}}""").search("책")
        }
    }
}
