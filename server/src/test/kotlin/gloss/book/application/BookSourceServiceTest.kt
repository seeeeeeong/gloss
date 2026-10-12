package gloss.book.application

import gloss.TestcontainersConfiguration
import gloss.book.FakeBookSourceGateway
import gloss.book.application.dto.BookSourceAddRequest
import gloss.book.application.dto.TocReviseRequest
import gloss.book.domain.BookSourceFailed
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class)
@Transactional
class BookSourceServiceTest(
    @Autowired private val bookSourceService: BookSourceService,
    @Autowired private val bookService: BookService,
    @Autowired private val fakeBookSourceGateway: FakeBookSourceGateway,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val clock: Clock,
) {
    @BeforeEach
    fun reset() {
        fakeBookSourceGateway.reset()
    }

    // Rolled back with the test; no one signs in, so the hash is never read.
    private fun accountId(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "y-" + id.toString().take(12))
            .update()
        return id
    }

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    @Test
    fun `a search lists the editions found, each saying whether its contents can be used`() {
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", isbn13 = "9788998139766", contents = null)
        fakeBookSourceGateway.holds("103", isbn13 = "9791158392239", contents = "1.2.3.4.5.6.7 너무 깊은 절")

        val found = bookSourceService.search(accountId(), "  오브젝트 ")

        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트")
        assertThat(
            found.map { it.ref to it.hasContents },
        ).containsExactly("101" to true, "102" to false, "103" to false)
        assertThat(found.first().source).isEqualTo("yes24")
        assertThat(found.first().url).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(found.first().isbn13).isEqualTo("9788966262472")
    }

    @Test
    fun `a search with no words, or too many, is refused before the source is asked`() {
        val account = accountId()

        assertFails(BookError.INVALID_QUERY) { bookSourceService.search(account, "   ") }
        assertFails(BookError.INVALID_QUERY) { bookSourceService.search(account, "가".repeat(101)) }
        assertThat(fakeBookSourceGateway.asked).isEmpty()
    }

    @Test
    fun `a book found in the source is added with its contents as a first draft and where they came from`() {
        fakeBookSourceGateway.holds("101")
        val account = accountId()

        val book = bookSourceService.add(account, BookSourceAddRequest(ref = "101"))
        entityManager.flush()

        assertThat(book.title).isEqualTo("오브젝트")
        assertThat(book.authors).containsExactly("조영호 저")
        assertThat(book.isbn13).isEqualTo("9788966262472")
        assertThat(book.source).isEqualTo("yes24")
        assertThat(book.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(book.currentTocId).isNull()
        assertThat(book.toc.versionNo).isEqualTo(1)
        assertThat(book.toc.origin).isEqualTo("api")
        assertThat(book.toc.status).isEqualTo("draft")
        assertThat(
            book.toc.items.map { it.depth to it.title },
        ).containsExactly(1 to "객체, 설계", 2 to "티켓 판매", 1 to "객체지향")
        val kept =
            jdbc
                .sql("select b.source_ref, t.source_url from book b join toc t on t.book_id = b.id where b.id = :id")
                .param("id", book.id)
                .query { row, _ -> row.getString(1) to row.getString(2) }
                .single()
        assertThat(kept).isEqualTo("101" to "https://www.yes24.com/product/goods/101")
    }

    @Test
    fun `an edit of sourced contents is the reader's own version, and the book still names its source`() {
        fakeBookSourceGateway.holds("101")
        val account = accountId()
        val added = bookSourceService.add(account, BookSourceAddRequest(ref = "101"))

        val revised = bookService.reviseToc(account, added.id, TocReviseRequest(toc = "1장 객체, 설계\n2장 객체지향"))

        assertThat(revised.toc.versionNo).isEqualTo(2)
        assertThat(revised.toc.origin).isEqualTo("manual")
        assertThat(revised.source).isEqualTo("yes24")
        assertThat(revised.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/101")
    }

    @Test
    fun `an edition is added with contents the reader typed, and still names its source`() {
        fakeBookSourceGateway.holds("102", contents = null)
        val account = accountId()

        val book = bookSourceService.add(account, BookSourceAddRequest(ref = "102", toc = "1장 객체\n1.1 티켓"))

        assertThat(book.source).isEqualTo("yes24")
        assertThat(book.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/102")
        assertThat(book.isbn13).isEqualTo("9788966262472")
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.toc.items.map { it.title }).containsExactly("객체", "티켓")
    }

    @Test
    fun `typed contents replace the source's own, and are checked as typed ones are`() {
        fakeBookSourceGateway.holds("101")
        val account = accountId()

        assertFails(BookError.TOC_TOO_DEEP) {
            bookSourceService.add(account, BookSourceAddRequest(ref = "101", toc = "1.2.3.4.5.6.7 너무 깊은 절"))
        }
        val book = bookSourceService.add(account, BookSourceAddRequest(ref = "101", toc = "1장 내가 쓴 목차"))

        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.toc.items.map { it.title }).containsExactly("내가 쓴 목차")
    }

    @Test
    fun `a book the source does not have, or has no usable contents for, is not added`() {
        fakeBookSourceGateway.holds("102", contents = null)
        val account = accountId()

        assertFails(
            BookError.SOURCE_BOOK_NOT_FOUND,
        ) { bookSourceService.add(account, BookSourceAddRequest(ref = "999")) }
        assertFails(BookError.SOURCE_NO_TOC) { bookSourceService.add(account, BookSourceAddRequest(ref = "102")) }
        assertFails(BookError.SOURCE_NO_TOC) {
            bookSourceService.add(account, BookSourceAddRequest(ref = "102", toc = "  "))
        }
        assertThat(bookService.list(account)).isEmpty()
    }

    @Test
    fun `a book already on the shelf by its isbn is not added again`() {
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("201")
        val account = accountId()
        bookSourceService.add(account, BookSourceAddRequest(ref = "101"))

        assertFails(BookError.ISBN_TAKEN) { bookSourceService.add(account, BookSourceAddRequest(ref = "201")) }
    }

    @Test
    fun `a source without a key, out of calls or failing is told to the reader as such`() {
        val account = accountId()

        fakeBookSourceGateway.failing = BookSourceFailed.Kind.UNAVAILABLE
        assertFails(BookError.SOURCE_UNAVAILABLE) { bookSourceService.search(account, "오브젝트") }
        fakeBookSourceGateway.failing = BookSourceFailed.Kind.BUSY
        assertFails(BookError.SOURCE_BUSY) { bookSourceService.search(account, "오브젝트") }
        fakeBookSourceGateway.failing = BookSourceFailed.Kind.FAILED
        assertFails(BookError.SOURCE_FAILED) { bookSourceService.add(account, BookSourceAddRequest(ref = "101")) }
    }

    @Test
    fun `a reader who asks more often than a minute allows is told to wait, and the source is not asked`() {
        val account = accountId()
        repeat(20) { bookSourceService.search(account, "오브젝트") }

        assertFails(BookError.SEARCH_TOO_OFTEN) { bookSourceService.search(account, "오브젝트") }
        assertThat(fakeBookSourceGateway.asked).hasSize(20)
        assertThat(bookSourceService.search(accountId(), "오브젝트")).isEmpty()
    }

    @Test
    fun `when every reader together has spent the key's pace, the next is told the source is busy`() {
        val paced = BookSourceThrottle(perMinute = 20, perSecond = 1, perDay = 100, clock = clock)
        val service = BookSourceService(fakeBookSourceGateway, paced, bookService)
        service.search(accountId(), "오브젝트")

        assertFails(BookError.SOURCE_BUSY) { service.search(accountId(), "오브젝트") }
        assertThat(fakeBookSourceGateway.asked).hasSize(1)
    }
}
