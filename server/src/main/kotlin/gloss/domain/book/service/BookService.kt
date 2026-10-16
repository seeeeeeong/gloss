package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.book.domain.Book
import gloss.domain.book.domain.BookDraft
import gloss.domain.book.domain.BookTitle
import gloss.domain.book.domain.Isbn13
import gloss.domain.book.domain.SourcedBook
import gloss.domain.book.domain.Toc
import gloss.domain.book.domain.TocItem
import gloss.domain.book.domain.TocParser
import gloss.domain.book.domain.TocText
import gloss.domain.book.domain.event.TocConfirmed
import gloss.domain.book.domain.repository.BookRepository
import gloss.domain.book.domain.repository.TocItemRepository
import gloss.domain.book.domain.repository.TocRepository
import gloss.domain.book.dto.request.BookCreateRequest
import gloss.domain.book.dto.request.TocReviseRequest
import gloss.domain.book.dto.response.BookResponse
import gloss.domain.book.dto.response.BookSummaryResponse
import gloss.domain.book.dto.response.TocResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** A reader's books and their contents, each version kept; only the reader who added a book sees it. */
@Service
@Transactional(readOnly = true)
class BookService(
    private val bookRepository: BookRepository,
    private val tocRepository: TocRepository,
    private val tocItemRepository: TocItemRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    /** A book typed in by the reader, with its contents as a first draft. */
    @Transactional
    fun create(
        accountId: UUID,
        request: BookCreateRequest,
    ): BookResponse = add(accountId = accountId, draft = draftOf(request), typed = request.toc, sourced = null)

    /**
     * A book and its contents as a first draft. [sourced] is the source its details came from, kept
     * with it; [typed] is the reader's own contents, and without them the source's are the draft.
     */
    @Transactional
    fun add(
        accountId: UUID,
        draft: BookDraft,
        typed: String?,
        sourced: SourcedBook?,
    ): BookResponse {
        val contents = read(typed ?: checkNotNull(sourced?.contents) { "Contents are typed or the source's" })
        val isbn13 = draft.isbn13
        if (isbn13 != null) {
            bookRepository.lockShelf(accountId)
            if (bookRepository.existsByAccountIdAndIsbn13AndDeletedAtIsNull(accountId, isbn13.value)) {
                throw GlossException(BookError.ISBN_TAKEN)
            }
        }
        val now = clock.instant()
        val book =
            sourced?.let { Book.createSourced(accountId = accountId, draft = draft, sourced = it, now = now) }
                ?: Book.createManual(accountId = accountId, draft = draft, now = now)
        bookRepository.save(book)
        val first =
            if (typed == null) {
                Toc.createSourced(book = book, text = contents.text, now = now)
            } else {
                Toc.createManual(book = book, versionNo = 1, text = contents.text, now = now)
            }
        return keep(book = book, toc = first, items = contents.items)
    }

    fun list(accountId: UUID): List<BookSummaryResponse> =
        bookRepository
            .findAllByAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(accountId)
            .map(BookSummaryResponse::from)

    // One snapshot for the book and its contents, so a confirm in between never shows half done.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun get(
        accountId: UUID,
        bookId: UUID,
    ): BookResponse {
        val book =
            bookRepository.findByIdAndAccountIdAndDeletedAtIsNull(bookId, accountId)
                ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        val toc = checkNotNull(tocRepository.findTopByBookIdAndAccountIdOrderByVersionNoDesc(book.id, accountId))
        return BookResponse.from(book, toc, tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(toc.id, accountId))
    }

    /** The contents posts are written from: a newer draft does not replace them until it is confirmed. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun currentToc(
        accountId: UUID,
        bookId: UUID,
    ): TocResponse {
        val book =
            bookRepository.findByIdAndAccountIdAndDeletedAtIsNull(bookId, accountId)
                ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        val tocId = book.currentTocId ?: throw GlossException(BookError.TOC_NOT_CONFIRMED)
        val toc = checkNotNull(tocRepository.findByIdAndAccountId(tocId, accountId))
        return TocResponse.from(toc, tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(toc.id, accountId))
    }

    /** An edit is a new draft version; the one in use stays until the reader confirms another. */
    @Transactional
    fun reviseToc(
        accountId: UUID,
        bookId: UUID,
        request: TocReviseRequest,
    ): BookResponse {
        val contents = read(request.toc)
        val book = bookRepository.findForUpdate(bookId, accountId) ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        val versionNo = tocRepository.lastVersionNo(book.id, book.accountId) + 1
        val edited = Toc.createManual(book = book, versionNo = versionNo, text = contents.text, now = clock.instant())
        return keep(book = book, toc = edited, items = contents.items)
    }

    /** Only the latest version: a reader who saw an older one confirms what they saw, or nothing. */
    @Transactional
    fun confirmToc(
        accountId: UUID,
        bookId: UUID,
        tocId: UUID,
    ): BookResponse {
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
        return BookResponse.from(
            book,
            latest,
            tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(latest.id, accountId),
        )
    }

    private fun keep(
        book: Book,
        toc: Toc,
        items: List<TocParser.Item>,
    ): BookResponse {
        tocRepository.save(toc)
        val kept = TocItem.itemsOf(toc, items, toc.createdAt)
        tocItemRepository.saveAll(kept)
        return BookResponse.from(book, toc, kept)
    }

    private fun draftOf(request: BookCreateRequest): BookDraft {
        val title = BookTitle.of(request.title) ?: throw GlossException(BookError.INVALID_TITLE)
        val typedIsbn = request.isbn13?.takeIf { it.isNotBlank() }
        val isbn13 = typedIsbn?.let(Isbn13::of)
        val authors = request.authors.map(String::trim).filter(String::isNotEmpty)
        val publisher = request.publisher?.trim()?.takeIf(String::isNotEmpty)
        val error =
            when {
                typedIsbn != null && isbn13 == null -> BookError.INVALID_ISBN
                authors.size > BookDraft.MAX_AUTHORS || !authors.all { BookDraft.fits(it) } -> BookError.INVALID_AUTHORS
                publisher != null && !BookDraft.fits(publisher) -> BookError.INVALID_PUBLISHER
                else -> return BookDraft(title = title, authors = authors, publisher = publisher, isbn13 = isbn13)
            }
        throw GlossException(error)
    }

    private fun read(raw: String): Contents {
        val text = TocText.of(raw) ?: throw GlossException(BookError.INVALID_TOC)
        val result = TocParser.parse(text.value)
        BookError.of(result)?.let { throw GlossException(it) }
        return Contents(text, (result as TocParser.Result.Parsed).items)
    }

    /** Contents as typed and as read into a tree, both checked before anything is locked or kept. */
    private class Contents(
        val text: TocText,
        val items: List<TocParser.Item>,
    )
}
