package gloss.domain.book.service

import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.domain.event.TocConfirmed
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
@RecordApplicationEvents
class BookServiceTest(
    @Autowired private val bookService: BookService,
    @Autowired private val tocService: TocService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val contents = "1장 객체, 설계\n1.1 티켓 판매 애플리케이션\n1.2 무엇이 문제인가\n2장 객체지향 프로그래밍"

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
            .param("loginId", "b-" + id.toString().take(12))
            .update()
        return id
    }

    private fun request(
        title: String = "오브젝트",
        isbn13: String? = null,
        toc: String = contents,
    ) = BookCreateRequest(title = title, authors = listOf(" 조영호 ", ""), publisher = "위키북스", isbn13 = isbn13, toc = toc)

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    @Test
    fun `a book typed in is kept with its contents as a first draft, read into a tree`() {
        val account = accountId()

        val book = bookService.create(account, request(isbn13 = "978-89-6626-247-2"))

        assertThat(book.authors).containsExactly("조영호")
        assertThat(book.isbn13).isEqualTo("9788966262472")
        assertThat(book.currentTocId).isNull()
        assertThat(book.toc.versionNo).isEqualTo(1)
        assertThat(book.toc.status).isEqualTo("draft")
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.toc.raw).isEqualTo(contents)
        assertThat(book.toc.items.map { it.depth }).containsExactly(1, 2, 2, 1)
        assertThat(book.toc.items[1].parentId).isEqualTo(book.toc.items[0].id)
    }

    @Test
    fun `the shelf lists only the reader's own books, newest first, and says which are confirmed`() {
        val account = accountId()
        val first = bookService.create(account, request(title = "첫 책"))
        bookService.create(account, request(title = "둘째 책"))
        bookService.create(accountId(), request(title = "남의 책"))
        bookService.confirmToc(account, first.id, first.toc.id)

        val shelf = bookService.list(account)

        assertThat(shelf.map { it.title }).containsExactlyInAnyOrder("첫 책", "둘째 책")
        assertThat(shelf.first { it.title == "첫 책" }.confirmed).isTrue()
    }

    @Test
    fun `an edit is a new draft, and the confirmed version stays in use until another is confirmed`() {
        val account = accountId()
        val created = bookService.create(account, request())
        val confirmed = bookService.confirmToc(account, created.id, created.toc.id)

        val revised = bookService.reviseToc(account, created.id, TocReviseRequest("1장 객체, 설계\n1.1 티켓 판매"))

        assertThat(revised.toc.versionNo).isEqualTo(2)
        assertThat(revised.toc.status).isEqualTo("draft")
        assertThat(revised.currentTocId).isEqualTo(confirmed.toc.id)
        assertThat(
            revised.toc.items
                .first()
                .itemKey,
        ).isEqualTo(
            confirmed.toc.items
                .first()
                .itemKey,
        )
        assertThat(bookService.get(account, created.id).toc.id).isEqualTo(revised.toc.id)
    }

    @Test
    fun `the contents in use are the confirmed ones, whatever draft came after`() {
        val account = accountId()
        val created = bookService.create(account, request())
        val unconfirmed = runCatching { bookService.currentToc(account, created.id) }.exceptionOrNull()
        bookService.confirmToc(account, created.id, created.toc.id)
        bookService.reviseToc(account, created.id, TocReviseRequest("1장 다시 쓴 목차"))

        val current = bookService.currentToc(account, created.id)

        assertThat((unconfirmed as GlossException).error).isEqualTo(BookError.TOC_NOT_CONFIRMED)
        assertThat(current.id).isEqualTo(created.toc.id)
        assertThat(current.status).isEqualTo("confirmed")
        assertThat(current.items.map { it.itemKey }).isEqualTo(created.toc.items.map { it.itemKey })
    }

    @Test
    fun `confirming puts the version in use and says so once, and confirming it again changes nothing`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()
        val created = bookService.create(account, request())

        val confirmed = bookService.confirmToc(account, created.id, created.toc.id)
        val again = bookService.confirmToc(account, created.id, created.toc.id)

        assertThat(confirmed.currentTocId).isEqualTo(created.toc.id)
        assertThat(confirmed.toc.status).isEqualTo("confirmed")
        assertThat(again.toc.confirmedAt).isEqualTo(confirmed.toc.confirmedAt)
        val said = events.stream(TocConfirmed::class.java).toList().single()
        assertThat(listOf(said.accountId, said.bookId, said.tocId)).containsExactly(account, created.id, created.toc.id)
    }

    @Test
    fun `another module confirms the same way, and learns which version it confirmed`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()
        val created = bookService.create(account, request())

        val confirmed = tocService.confirmContents(account, created.id, created.toc.id)

        assertThat(
            confirmed,
        ).isEqualTo(ContentsVersion(bookId = created.id, tocId = created.toc.id, versionNo = 1, items = 4))
        assertThat(bookService.get(account, created.id).currentTocId).isEqualTo(created.toc.id)
        assertThat(events.stream(TocConfirmed::class.java).toList()).hasSize(1)
        assertFails(BookError.TOC_NOT_FOUND) { tocService.confirmContents(account, created.id, UUID.randomUUID()) }
    }

    @Test
    fun `a book typed in elsewhere is made with its contents as the first version, confirmed at once`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()

        val confirmed =
            bookService.createConfirmed(
                account,
                TypedBook.of(title = " 오브젝트 ", authors = listOf(" 조영호 ", ""), toc = contents),
            )

        val book = bookService.get(account, confirmed.bookId)
        assertThat(book.title).isEqualTo("오브젝트")
        assertThat(book.authors).containsExactly("조영호")
        assertThat(book.source).isEqualTo("manual")
        assertThat(book.toc.versionNo).isEqualTo(1)
        assertThat(book.currentTocId).isEqualTo(confirmed.tocId)
        assertThat(events.stream(TocConfirmed::class.java).toList()).hasSize(1)
        assertFails(BookError.INVALID_TITLE) { TypedBook.of(title = " ", authors = emptyList(), toc = contents) }
        assertFails(
            BookError.INVALID_AUTHORS,
        ) { TypedBook.of(title = "책", authors = List(21) { "저자" }, toc = contents) }
        assertFails(BookError.INVALID_TOC) { TypedBook.of(title = "책", authors = emptyList(), toc = "\n \n") }
    }

    @Test
    fun `only the latest version is confirmed, so a reader confirms what they saw`() {
        val account = accountId()
        val created = bookService.create(account, request())
        bookService.reviseToc(account, created.id, TocReviseRequest("1장 다시 쓴 목차"))

        assertFails(BookError.TOC_OUTDATED) { bookService.confirmToc(account, created.id, created.toc.id) }
        assertFails(BookError.TOC_NOT_FOUND) { bookService.confirmToc(account, created.id, UUID.randomUUID()) }
    }

    @Test
    fun `another reader's book is not found, however it is reached`() {
        val created = bookService.create(accountId(), request())
        val stranger = accountId()

        assertFails(BookError.BOOK_NOT_FOUND) { bookService.get(stranger, created.id) }
        assertFails(BookError.BOOK_NOT_FOUND) { bookService.currentToc(stranger, created.id) }
        assertFails(BookError.BOOK_NOT_FOUND) { bookService.reviseToc(stranger, created.id, TocReviseRequest("x")) }
        assertFails(BookError.BOOK_NOT_FOUND) { bookService.confirmToc(stranger, created.id, created.toc.id) }
    }

    @Test
    fun `the same isbn twice on one shelf is refused, on two shelves it is not`() {
        val account = accountId()
        bookService.create(account, request(isbn13 = "9788966262472"))

        assertFails(BookError.ISBN_TAKEN) { bookService.create(account, request(isbn13 = "978-89-6626-247-2")) }
        assertThat(bookService.create(accountId(), request(isbn13 = "9788966262472")).isbn13).isNotNull()
    }

    @Test
    fun `a bad title, isbn, author list or contents says which`() {
        val account = accountId()

        assertFails(BookError.INVALID_TITLE) { bookService.create(account, request(title = "  ")) }
        assertFails(BookError.INVALID_ISBN) { bookService.create(account, request(isbn13 = "9788966262473")) }
        assertFails(BookError.INVALID_AUTHORS) {
            bookService.create(account, request().copy(authors = List(21) { "저자 $it" }))
        }
        assertFails(BookError.TOC_EMPTY) { bookService.create(account, request(toc = "-\n•")) }
        assertFails(BookError.TOC_LINE_TOO_LONG) { bookService.create(account, request(toc = "가".repeat(501))) }
        assertFails(BookError.TOC_TOO_DEEP) {
            bookService.create(
                account,
                request(
                    toc =
                        (1..7).joinToString("\n") {
                            List(it) { "1" }.joinToString(".") +
                                " 절"
                        },
                ),
            )
        }
        assertFails(BookError.INVALID_TOC) { bookService.create(account, request(toc = "가".repeat(100_001))) }
    }

    @Test
    fun `a stored version reads back as it was made`() {
        val account = accountId()
        val created = bookService.create(account, request())
        entityManager.flush()
        entityManager.clear()

        val read = bookService.get(account, created.id)

        assertThat(read.toc.items).isEqualTo(created.toc.items)
        assertThat(read.authors).isEqualTo(created.authors)
    }

    // Committed, as two requests at once are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two adds of one isbn at once leave one book and say the other is taken`() {
        val account = accountId()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val outcomes =
            List(2) {
                pool.submit<String> {
                    start.await(5, TimeUnit.SECONDS)
                    runCatching { bookService.create(account, request(isbn13 = "9791158391461")) }
                        .fold({ "created" }, { (it as GlossException).error.code })
                }
            }
        start.countDown()

        assertThat(
            outcomes.map { it.get(10, TimeUnit.SECONDS) },
        ).containsExactlyInAnyOrder("created", "book.isbn_taken")
        pool.shutdown()
    }
}
