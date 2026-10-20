package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.book.domain.event.TocConfirmed
import gloss.domain.book.domain.repository.BookRepository
import gloss.domain.book.domain.repository.TocItemRepository
import gloss.domain.book.domain.repository.TocRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** A book's contents as other modules read and confirm them. */
@Service
class TocService(
    private val bookRepository: BookRepository,
    private val tocRepository: TocRepository,
    private val tocItemRepository: TocItemRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    /** Only the latest version: a reader who saw an older one confirms what they saw, or nothing. */
    @Transactional
    fun confirmContents(
        accountId: UUID,
        bookId: UUID,
        tocId: UUID,
    ): ContentsVersion {
        val book = bookRepository.findForUpdate(bookId, accountId) ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        val latest = checkNotNull(tocRepository.findTopByBookIdAndAccountIdOrderByVersionNoDesc(book.id, accountId))
        if (latest.id != tocId) {
            val known = tocRepository.existsByIdAndBookIdAndAccountId(tocId, book.id, accountId)
            throw GlossException(if (known) BookError.TOC_OUTDATED else BookError.TOC_NOT_FOUND)
        }
        val now = clock.instant()
        val confirmed = latest.confirm(now)
        book.use(latest)
        // Once: confirming again changes nothing, and nothing is to be written from it again.
        if (confirmed) {
            applicationEventPublisher.publishEvent(
                TocConfirmed(
                    eventId = UuidV7.at(now),
                    occurredAt = now,
                    accountId = accountId,
                    bookId = book.id,
                    tocId = latest.id,
                ),
            )
        }
        return ContentsVersion(
            bookId = book.id,
            tocId = latest.id,
            versionNo = latest.versionNo,
            items = tocItemRepository.countByTocIdAndAccountId(latest.id, accountId).toInt(),
        )
    }

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

    /** Which version of the contents is in use, unheld; null when the book is gone or none is confirmed yet. */
    @Transactional(readOnly = true)
    fun inUse(
        accountId: UUID,
        bookId: UUID,
    ): ContentsInUse? {
        val tocId =
            bookRepository.findByIdAndAccountIdAndArchivedAtIsNull(bookId, accountId)?.currentTocId ?: return null
        val toc = checkNotNull(tocRepository.findByIdAndAccountId(tocId, accountId))
        return ContentsInUse(tocId = toc.id, confirmedAt = checkNotNull(toc.confirmedAt))
    }

    /** The contents in use, in order; null when the book is gone or none are confirmed yet. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun outline(
        accountId: UUID,
        bookId: UUID,
    ): BookOutline? {
        val book = bookRepository.findByIdAndAccountIdAndArchivedAtIsNull(bookId, accountId) ?: return null
        val tocId = book.currentTocId ?: return null
        return BookOutline.of(book, tocId, tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(tocId, accountId))
    }
}
