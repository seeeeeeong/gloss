package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.repository.BookRepository
import gloss.domain.book.domain.repository.TocItemRepository
import gloss.domain.book.dto.request.BookReadRequest
import gloss.domain.book.dto.response.BookReadResponse
import gloss.domain.book.dto.response.RecentBookResponse
import gloss.domain.book.infrastructure.JdbcBookReads
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Where a reader is in each book, kept by the server so the book opens there on any of their devices. */
@Service
class BookReadService(
    private val bookRepository: BookRepository,
    private val tocItemRepository: TocItemRepository,
    private val jdbcBookReads: JdbcBookReads,
    private val clock: Clock,
) {
    /** The item is one of the contents in use; the last one kept is where the book opens. */
    @Transactional
    fun keep(
        accountId: UUID,
        bookId: UUID,
        request: BookReadRequest,
    ) {
        val tocId = tocInUse(accountId, bookId)
        if (!tocItemRepository.existsByTocIdAndAccountIdAndItemKey(tocId, accountId, request.itemKey)) {
            throw GlossException(BookError.ITEM_NOT_FOUND)
        }
        jdbcBookReads.keep(accountId = accountId, bookId = bookId, itemKey = request.itemKey, at = clock.instant())
    }

    @Transactional(readOnly = true)
    fun find(
        accountId: UUID,
        bookId: UUID,
    ): BookReadResponse {
        val read = jdbcBookReads.find(accountId, bookId) ?: throw GlossException(BookError.NOT_READ)
        return BookReadResponse.from(read)
    }

    /** The books read last, newest first. */
    @Transactional(readOnly = true)
    fun recent(accountId: UUID): List<RecentBookResponse> =
        jdbcBookReads.recent(accountId, RECENT).map(RecentBookResponse::from)

    private fun tocInUse(
        accountId: UUID,
        bookId: UUID,
    ): UUID {
        val book =
            bookRepository.findByIdAndAccountIdAndDeletedAtIsNull(bookId, accountId)
                ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        return book.currentTocId ?: throw GlossException(BookError.TOC_NOT_CONFIRMED)
    }

    private companion object {
        // What the sidebar lists; every book is on the shelf.
        const val RECENT = 10
    }
}
