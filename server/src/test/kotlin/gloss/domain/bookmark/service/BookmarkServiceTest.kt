package gloss.domain.bookmark.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.service.BookService
import gloss.domain.book.service.BookShelfService
import gloss.domain.book.service.TocService
import gloss.domain.book.service.TypedBook
import gloss.domain.bookmark.dto.request.BookmarkCreateRequest
import gloss.domain.bookmark.dto.response.BookmarkResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class BookmarkServiceTest(
    @Autowired private val bookmarkService: BookmarkService,
    @Autowired private val bookService: BookService,
    @Autowired private val bookShelfService: BookShelfService,
    @Autowired private val tocService: TocService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
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
            .param("loginId", "bk-" + id.toString().take(12))
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

    private fun save(
        book: Shelved,
        key: String = book.keys[1],
        quote: String? = null,
    ): BookmarkResponse =
        bookmarkService.save(book.account, BookmarkCreateRequest(bookId = book.id, itemKey = key, quote = quote))

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    @Test
    fun `a phrase is saved on its item, trimmed, and named as the contents name the item`() {
        val book = book()

        val saved = save(book, quote = "  관람객의 가방\n")

        assertThat(saved.bookId).isEqualTo(book.id)
        assertThat(saved.bookTitle).isEqualTo("오브젝트")
        assertThat(saved.itemKey).isEqualTo(book.keys[1])
        assertThat(saved.itemNumber).isEqualTo("1.1")
        assertThat(saved.itemTitle).isEqualTo("티켓 판매")
        assertThat(saved.quote).isEqualTo("관람객의 가방")
        assertThat(saved.createdAt).isEqualTo(clock.instant())
        assertThat(bookmarkService.list(book.account)).containsExactly(saved)
    }

    @Test
    fun `saving the same phrase of an item again returns the one already saved`() {
        val book = book()
        val first = save(book, quote = "관람객의 가방")
        clock.advance(Duration.ofMinutes(1))

        val again = save(book, quote = " 관람객의 가방 ")
        val otherPhrase = save(book, quote = "초대장")
        val otherItem = save(book, key = book.keys[2], quote = "관람객의 가방")

        assertThat(again).isEqualTo(first)
        assertThat(listOf(otherPhrase.id, otherItem.id)).doesNotContain(first.id)
        assertThat(bookmarkService.list(book.account)).hasSize(3)
    }

    @Test
    fun `saving an item itself again returns the one already saved`() {
        val book = book()
        val first = save(book)
        clock.advance(Duration.ofMinutes(1))

        val again = save(book)

        assertThat(again).isEqualTo(first)
        assertThat(first.quote).isNull()
        assertThat(bookmarkService.list(book.account)).hasSize(1)
    }

    @Test
    fun `a blank phrase saves the item itself`() {
        val book = book()

        val blank = save(book, quote = " \n ")
        val item = save(book)

        assertThat(blank.quote).isNull()
        assertThat(item.id).isEqualTo(blank.id)
    }

    @Test
    fun `an item not in the confirmed contents is refused`() {
        val book = book()

        assertFails(BookmarkError.ITEM_NOT_FOUND) { save(book, key = "1장 객체 > 1.2 없는 절") }
    }

    @Test
    fun `a book without confirmed contents, or another reader's, is refused`() {
        val book = book()
        val account = accountId()
        val draft = bookService.create(account, BookCreateRequest(title = "오브젝트", toc = contents))
        val unconfirmed = Shelved(account, draft.id, book.keys)
        val strangers = Shelved(accountId(), book.id, book.keys)

        assertFails(BookmarkError.BOOK_NOT_READY) { save(unconfirmed) }
        assertFails(BookmarkError.BOOK_NOT_READY) { save(strangers) }
    }

    @Test
    fun `a reader's bookmarks are listed newest first, each with its book's title and its item's`() {
        val objects = book()
        val roles = book(account = objects.account, title = "객체지향의 사실과 오해")
        save(objects, key = objects.keys[0])
        clock.advance(Duration.ofMinutes(1))
        save(roles, key = roles.keys[2], quote = "역할과 책임")
        clock.advance(Duration.ofMinutes(1))
        save(objects, quote = "관람객의 가방")
        save(book(), quote = "다른 독자의 문장")

        val listed = bookmarkService.list(objects.account)
        val named = listed.map { "${it.bookTitle} | ${it.itemNumber} | ${it.itemTitle} | ${it.quote}" }

        assertThat(named).containsExactly(
            "오브젝트 | 1.1 | 티켓 판매 | 관람객의 가방",
            "객체지향의 사실과 오해 | 2장 | 설계 | 역할과 책임",
            "오브젝트 | 1장 | 객체 | null",
        )
    }

    @Test
    fun `an item the contents in use left out is named by the last part of its key`() {
        val book = book()
        save(book, quote = "관람객의 가방")
        val revised = bookService.reviseToc(book.account, book.id, "1장 객체\n1.1 티켓 판매하기\n2장 설계")
        tocService.confirmContents(book.account, book.id, revised.toc.id)

        val listed = bookmarkService.list(book.account).single()

        assertThat(listed.bookTitle).isEqualTo("오브젝트")
        assertThat(listed.itemNumber).isNull()
        assertThat(listed.itemTitle).isEqualTo("1.1 티켓 판매")
    }

    @Test
    fun `a book taken off the shelf takes its bookmarks with it until it is back`() {
        val book = book()
        val saved = save(book, quote = "관람객의 가방")
        bookShelfService.archive(book.account, book.id)

        val hidden = bookmarkService.list(book.account)
        jdbc.sql("update book set archived_at = null where id = :id").param("id", book.id).update()

        assertThat(hidden).isEmpty()
        assertThat(bookmarkService.list(book.account)).containsExactly(saved)
    }

    @Test
    fun `a reader deletes only their own bookmark`() {
        val book = book()
        val saved = save(book, quote = "관람객의 가방")

        assertFails(BookmarkError.NOT_FOUND) { bookmarkService.delete(accountId(), saved.id) }
        assertThat(bookmarkService.list(book.account)).containsExactly(saved)

        bookmarkService.delete(book.account, saved.id)

        assertThat(bookmarkService.list(book.account)).isEmpty()
        assertFails(BookmarkError.NOT_FOUND) { bookmarkService.delete(book.account, saved.id) }
    }

    // Committed, as requests at once are: nothing here is rolled back. Four, as two seldom meet.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `saves of one phrase sent at once keep one bookmark`() {
        val book = book()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val saves =
            List(4) {
                pool.submit<UUID> {
                    start.await(5, TimeUnit.SECONDS)
                    save(book, quote = "관람객의 가방").id
                }
            }
        start.countDown()

        val ids = saves.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(ids.distinct()).hasSize(1)
        assertThat(bookmarkService.list(book.account)).hasSize(1)
    }
}
