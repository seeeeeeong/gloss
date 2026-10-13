package gloss.book.application

import gloss.book.domain.BookRepository
import gloss.book.domain.TocItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** A book's contents as other modules read them. */
@Service
class TocService(
    private val bookRepository: BookRepository,
    private val tocItemRepository: TocItemRepository,
) {
    /**
     * Which version of the contents is in use, held so until the caller's transaction ends: a confirm
     * of another version waits, so what the caller writes for this one is written while it is in use.
     * Null when the book is gone or none is confirmed yet.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun holdCurrentTocId(
        accountId: UUID,
        bookId: UUID,
    ): UUID? = bookRepository.findForUpdate(bookId, accountId)?.currentTocId

    /** The contents in use, in order; null when the book is gone or none are confirmed yet. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun outline(
        accountId: UUID,
        bookId: UUID,
    ): BookOutline? {
        val book = bookRepository.findByIdAndAccountIdAndDeletedAtIsNull(bookId, accountId) ?: return null
        val tocId = book.currentTocId ?: return null
        return BookOutline.of(book, tocId, tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(tocId, accountId))
    }
}
