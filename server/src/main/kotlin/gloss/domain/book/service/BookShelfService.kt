package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.Book
import gloss.domain.book.domain.Isbn13
import gloss.domain.book.domain.repository.BookRepository
import gloss.domain.book.domain.repository.TocItemRepository
import gloss.domain.book.domain.repository.TocRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Taking a book off the reader's shelf and putting it back: hidden, never deleted, since its posts and costs stay. */
@Service
@Transactional(readOnly = true)
class BookShelfService(
    private val bookRepository: BookRepository,
    private val tocRepository: TocRepository,
    private val tocItemRepository: TocItemRepository,
    private val tocService: TocService,
    private val clock: Clock,
) {
    /** Takes the book off the reader's shelf; adding it again puts it back. Not found for anyone else's. */
    @Transactional
    fun archive(
        accountId: UUID,
        bookId: UUID,
    ) {
        val book = bookRepository.findForUpdate(bookId, accountId) ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        book.archive(clock.instant())
    }

    /**
     * The book with [isbn13] the reader took off their shelf, back on it as it was: the contents it was
     * read from, or its latest confirmed now when none were. Null when there is none, or one is on it.
     */
    @Transactional
    fun restore(
        accountId: UUID,
        isbn13: Isbn13,
    ): ContentsVersion? {
        bookRepository.lockShelf(accountId)
        val book = restored(accountId, isbn13) ?: return null
        val tocId = book.currentTocId
        return if (tocId == null) {
            val latest = checkNotNull(tocRepository.findTopByBookIdAndAccountIdOrderByVersionNoDesc(book.id, accountId))
            tocService.confirmContents(accountId, book.id, latest.id)
        } else {
            val toc = checkNotNull(tocRepository.findByIdAndAccountId(tocId, accountId))
            ContentsVersion(
                bookId = book.id,
                tocId = toc.id,
                versionNo = toc.versionNo,
                items = tocItemRepository.countByTocIdAndAccountId(toc.id, accountId).toInt(),
            )
        }
    }

    /** The archived book with [isbn13], back on the shelf; the caller holds the shelf's lock. */
    @Transactional
    fun restored(
        accountId: UUID,
        isbn13: Isbn13,
    ): Book? {
        if (bookRepository.existsByAccountIdAndIsbn13AndArchivedAtIsNull(accountId, isbn13.value)) return null
        return bookRepository
            .findFirstByAccountIdAndIsbn13AndArchivedAtIsNotNullOrderByArchivedAtDesc(accountId, isbn13.value)
            ?.also(Book::restore)
    }
}
