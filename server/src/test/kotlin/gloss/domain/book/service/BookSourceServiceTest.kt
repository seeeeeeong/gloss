package gloss.domain.book.service

import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookSourceAddRequest
import gloss.domain.book.dto.request.TocReviseRequest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class)
@Transactional
class BookSourceServiceTest(
    @Autowired private val bookSourceService: BookSourceService,
    @Autowired private val bookService: BookService,
    @Autowired private val fakeBookSourceGateway: FakeBookSourceGateway,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
    // The few beans only the hand-made service below needs.
    @Autowired private val applicationContext: ApplicationContext,
) {
    @BeforeEach
    fun reset() {
        fakeBookSourceGateway.reset()
        applicationContext.getBean(EditionCache::class.java).clear()
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
    fun `the same search gives editions to another module, with their covers`() {
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", isbn13 = "9788998139766", contents = null)

        val found = bookSourceService.editions(accountId(), " 오브젝트 ")

        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트")
        assertThat(found.map { it.ref to it.hasContents }).containsExactly("101" to true, "102" to false)
        assertThat(found.first().cover).isEqualTo("https://image.yes24.com/goods/101/L")
        assertThat(found.first().isbn13).isEqualTo("9788966262472")
    }

    @Test
    fun `the same words asked again soon are answered from memory, without asking the source`() {
        fakeBookSourceGateway.holds("101")
        val account = accountId()

        val first = bookSourceService.editions(account, "오브젝트")
        val again = bookSourceService.editions(account, " 오브젝트 ")

        assertThat(again).isEqualTo(first)
        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트")
    }

    @Test
    fun `an edition is read first and kept later, its source's contents the first version and confirmed`() {
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", contents = null)
        val account = accountId()

        val edition = bookSourceService.fetch(account, "101")
        val kept =
            bookSourceService.keep(
                accountId = account,
                edition = edition,
                contents = checkNotNull(edition.contents),
                style = WritingStyle(WritingStyle.Level.INTRO, WritingStyle.Voice.EXPLAIN),
            )

        val book = bookService.get(account, kept.bookId)
        assertThat(edition.ref).isEqualTo("101")
        assertThat(kept.tocId).isEqualTo(book.toc.id)
        assertThat(book.currentTocId).isEqualTo(book.toc.id)
        assertThat(kept.versionNo).isEqualTo(1)
        assertThat(kept.items).isEqualTo(3)
        assertThat(book.toc.origin).isEqualTo("api")
        assertThat(book.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(book.level to book.voice).isEqualTo("intro" to "explain")
        assertFails(BookError.SOURCE_BOOK_NOT_FOUND) { bookSourceService.fetch(account, "999") }
        assertThat(bookSourceService.fetch(account, "102").contents).isNull()
    }

    @Test
    fun `contents the reader changed before keeping are a second version after the source's`() {
        fakeBookSourceGateway.holds("101")
        val account = accountId()
        val edition = bookSourceService.fetch(account, "101")

        val kept =
            bookSourceService.keep(
                accountId = account,
                edition = edition,
                contents = "1장 객체\n2장 설계",
                style = WritingStyle.DEFAULT,
            )

        val book = bookService.get(account, kept.bookId)
        assertThat(kept.versionNo).isEqualTo(2)
        assertThat(kept.items).isEqualTo(2)
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.source).isEqualTo("yes24")
    }

    @Test
    fun `an edition the source has no contents for is kept with the reader's as the first version`() {
        fakeBookSourceGateway.holds("102", contents = null)
        val account = accountId()

        val edition = bookSourceService.fetch(account, "102")
        val kept =
            bookSourceService.keep(
                accountId = account,
                edition = edition,
                contents = "1장 객체\n2장 설계",
                style = WritingStyle.DEFAULT,
            )

        val book = bookService.get(account, kept.bookId)
        assertThat(kept.versionNo).isEqualTo(1)
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.source).isEqualTo("yes24")
        assertThat(book.currentTocId).isEqualTo(kept.tocId)
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
    fun `a reader over their pace is refused alone, while another reader's ask of the same words goes on`() {
        fakeBookSourceGateway.holds("101")
        val busy = accountId()
        val other = accountId()
        repeat(20) { bookSourceService.search(busy, "오브젝트") }
        fakeBookSourceGateway.asked.clear()
        val gate = CountDownLatch(1)
        fakeBookSourceGateway.holdCalls(gate)
        val pool = Executors.newSingleThreadExecutor()
        val leading = pool.submit<List<Edition>> { bookSourceService.editions(other, "객체지향") }
        fakeBookSourceGateway.calling.await(5, TimeUnit.SECONDS)

        assertFails(BookError.SEARCH_TOO_OFTEN) { bookSourceService.editions(busy, "객체지향") }
        gate.countDown()

        assertThat(leading.get(5, TimeUnit.SECONDS).map { it.ref }).containsExactly("101")
        assertThat(fakeBookSourceGateway.asked).containsExactly("객체지향")
        pool.shutdown()
    }

    @Test
    fun `nine searches of the same words at once spend the key's pace once, so none is told the source is busy`() {
        fakeBookSourceGateway.holds("101")
        val clock = applicationContext.getBean(Clock::class.java)
        val service =
            BookSourceService(
                bookSourceGateway = fakeBookSourceGateway,
                bookSourceThrottle = BookSourceThrottle(perMinute = 20, perSecond = 1, perDay = 100, clock = clock),
                bookService = bookService,
                bookShelfService = applicationContext.getBean(BookShelfService::class.java),
                tocService = applicationContext.getBean(TocService::class.java),
                editionCache = EditionCache(clock),
            )
        val gate = CountDownLatch(1)
        val arrived = CountDownLatch(9)
        fakeBookSourceGateway.holdCalls(gate)
        val pool = Executors.newFixedThreadPool(9)
        val answers =
            List(9) {
                pool.submit<String> {
                    arrived.countDown()
                    runCatching { service.editions(UUID.randomUUID(), "객체지향") }
                        .fold({ found -> found.joinToString { it.ref } }, { (it as GlossException).error.code })
                }
            }
        fakeBookSourceGateway.calling.await(5, TimeUnit.SECONDS)
        // A search that arrives after the answer finds it remembered and spends nothing either.
        arrived.await(5, TimeUnit.SECONDS)
        gate.countDown()

        assertThat(answers.map { it.get(10, TimeUnit.SECONDS) }).containsOnly("101")
        assertThat(fakeBookSourceGateway.asked).containsExactly("객체지향")
        pool.shutdown()
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
        val clock = applicationContext.getBean(Clock::class.java)
        val paced = BookSourceThrottle(perMinute = 20, perSecond = 1, perDay = 100, clock = clock)
        val service =
            BookSourceService(
                bookSourceGateway = fakeBookSourceGateway,
                bookSourceThrottle = paced,
                bookService = bookService,
                bookShelfService = applicationContext.getBean(BookShelfService::class.java),
                tocService = applicationContext.getBean(TocService::class.java),
                editionCache = EditionCache(clock),
            )
        service.search(accountId(), "오브젝트")

        assertFails(BookError.SOURCE_BUSY) { service.search(accountId(), "오브젝트") }
        assertThat(fakeBookSourceGateway.asked).hasSize(1)
    }
}
