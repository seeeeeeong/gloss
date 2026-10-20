package gloss.domain.book.service

import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.domain.Isbn13
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class BookShelfServiceTest(
    @Autowired private val bookShelfService: BookShelfService,
    @Autowired private val bookService: BookService,
    @Autowired private val tocService: TocService,
    @Autowired private val jdbc: JdbcClient,
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
    fun `a book taken off the shelf is gone from it, however it is reached, and only its reader can take it`() {
        val account = accountId()
        val created = bookService.create(account, request())

        assertFails(BookError.BOOK_NOT_FOUND) { bookShelfService.archive(accountId(), created.id) }
        bookShelfService.archive(account, created.id)

        assertThat(bookService.list(account)).isEmpty()
        assertFails(BookError.BOOK_NOT_FOUND) { bookService.get(account, created.id) }
        assertFails(BookError.BOOK_NOT_FOUND) { bookService.currentToc(account, created.id) }
        assertFails(BookError.BOOK_NOT_FOUND) { bookShelfService.archive(account, created.id) }
        assertThat(tocService.outline(account, created.id)).isNull()
    }

    @Test
    fun `restoring finds the book taken off the shelf, with the contents it was read from`() {
        val account = accountId()
        val isbn = checkNotNull(Isbn13.of("9788966262472"))
        val created = bookService.create(account, request(isbn13 = isbn.value))
        bookService.confirmToc(account, created.id, created.toc.id)
        bookService.reviseToc(account, created.id, TocReviseRequest("1장 고친 목차"))

        assertThat(bookShelfService.restore(account, isbn)).isNull()
        bookShelfService.archive(account, created.id)
        val restored = checkNotNull(bookShelfService.restore(account, isbn))

        assertThat(restored.bookId).isEqualTo(created.id)
        assertThat(restored.tocId).isEqualTo(created.toc.id)
        assertThat(restored.versionNo).isEqualTo(1)
        assertThat(bookService.get(account, created.id).currentTocId).isEqualTo(created.toc.id)
        assertThat(bookShelfService.restore(accountId(), isbn)).isNull()
    }

    @Test
    fun `a book taken off the shelf before its contents were confirmed comes back with them confirmed`() {
        val account = accountId()
        val isbn = checkNotNull(Isbn13.of("9788966262472"))
        val created = bookService.create(account, request(isbn13 = isbn.value))
        bookShelfService.archive(account, created.id)

        val restored = checkNotNull(bookShelfService.restore(account, isbn))

        assertThat(restored.tocId).isEqualTo(created.toc.id)
        assertThat(bookService.get(account, created.id).currentTocId).isEqualTo(created.toc.id)
    }
}
