package gloss.domain.book.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.BookReadRequest
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
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class BookReadServiceTest(
    @Autowired private val bookReadService: BookReadService,
    @Autowired private val bookService: BookService,
    @Autowired private val tocService: TocService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val contents = "1장 객체\n1.1 티켓 판매\n2장 설계"

    @BeforeEach
    fun resetClock() {
        clock.reset()
    }

    // No one signs in, so the hash is never read.
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
            .param("loginId", "rd-" + id.toString().take(12))
            .update()
        return id
    }

    private class Shelved(
        val account: UUID,
        val id: UUID,
        val keys: List<String>,
    )

    /** Contents confirmed: 1장 객체, then 1.1 티켓 판매 under it, then 2장 설계. */
    private fun book(
        account: UUID = accountId(),
        title: String = "오브젝트",
    ): Shelved {
        val typed = TypedBook.of(title = title, authors = listOf("조영호"), toc = contents)
        val confirmed = bookService.createConfirmed(account, typed, WritingStyle.DEFAULT)
        val outline = checkNotNull(tocService.outline(account, confirmed.bookId))
        return Shelved(account, confirmed.bookId, outline.items.map { it.itemKey })
    }

    private fun read(
        book: Shelved,
        key: String = book.keys[1],
    ) = bookReadService.keep(book.account, book.id, BookReadRequest(key))

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    @Test
    fun `a book opens at the item last read in it`() {
        val book = book()
        read(book)
        clock.advance(Duration.ofMinutes(5))

        read(book, key = book.keys[2])

        val found = bookReadService.find(book.account, book.id)
        assertThat(found.itemKey).isEqualTo(book.keys[2])
        assertThat(found.readAt).isEqualTo(clock.instant())
    }

    @Test
    fun `a book not read yet has no place to open at`() {
        val book = book()

        assertFails(BookError.NOT_READ) { bookReadService.find(book.account, book.id) }
        assertFails(BookError.NOT_READ) { bookReadService.find(accountId(), book.id) }
    }

    @Test
    fun `an item not in the contents in use is refused`() {
        val book = book()

        assertFails(BookError.ITEM_NOT_FOUND) { read(book, key = "1장 객체 > 1.2 없는 절") }
    }

    @Test
    fun `a book without confirmed contents, or another reader's, is refused`() {
        val book = book()
        val account = accountId()
        val draft = bookService.create(account, BookCreateRequest(title = "오브젝트", toc = contents))

        assertFails(BookError.TOC_NOT_CONFIRMED) {
            bookReadService.keep(account, draft.id, BookReadRequest(book.keys[1]))
        }
        assertFails(
            BookError.BOOK_NOT_FOUND,
        ) { bookReadService.keep(accountId(), book.id, BookReadRequest(book.keys[1])) }
    }

    @Test
    fun `the books read lately are listed newest first, each with the item last read in it`() {
        val objects = book()
        val roles = book(account = objects.account, title = "객체지향의 사실과 오해")
        book(account = objects.account, title = "읽지 않은 책")
        read(objects, key = objects.keys[0])
        clock.advance(Duration.ofMinutes(1))
        read(roles, key = roles.keys[2])
        clock.advance(Duration.ofMinutes(1))
        read(objects)
        read(book())

        val listed = bookReadService.recent(objects.account)
        val named = listed.map { "${it.title} | ${it.itemNumber} | ${it.itemTitle}" }

        assertThat(named).containsExactly("오브젝트 | 1.1 | 티켓 판매", "객체지향의 사실과 오해 | 2장 | 설계")
        assertThat(listed.first().bookId).isEqualTo(objects.id)
        assertThat(listed.first().readAt).isEqualTo(clock.instant())
    }

    @Test
    fun `a book taken off the shelf leaves the books read lately, and comes back with its place`() {
        val book = book()
        read(book)
        jdbc.sql("update book set archived_at = now() where id = :id").param("id", book.id).update()

        val hidden = bookReadService.recent(book.account)
        assertFails(BookError.NOT_READ) { bookReadService.find(book.account, book.id) }
        jdbc.sql("update book set archived_at = null where id = :id").param("id", book.id).update()

        assertThat(hidden).isEmpty()
        assertThat(bookReadService.recent(book.account).map { it.bookId }).containsExactly(book.id)
    }

    @Test
    fun `only the ten books read last are listed`() {
        val account = accountId()
        val books =
            List(11) { n ->
                clock.advance(Duration.ofMinutes(1))
                book(account = account, title = "책 $n").also { read(it) }
            }

        val listed = bookReadService.recent(account)

        assertThat(listed.map { it.bookId }).containsExactlyElementsOf(books.drop(1).reversed().map { it.id })
    }

    @Test
    fun `an item the contents in use left out is named by the last part of its key`() {
        val book = book()
        read(book)
        val revised = bookService.reviseToc(book.account, book.id, "1장 객체\n1.1 티켓 판매하기\n2장 설계")
        tocService.confirmContents(book.account, book.id, revised.toc.id)
        // The list reads past the persistence context, as a later request would.
        entityManager.flush()

        val listed = bookReadService.recent(book.account).single()

        assertThat(listed.itemKey).isEqualTo(book.keys[1])
        assertThat(listed.itemNumber).isNull()
        assertThat(listed.itemTitle).isEqualTo("1.1 티켓 판매")
    }
}
