package gloss.book.application

import gloss.book.application.dto.BookCreateRequest
import gloss.book.application.dto.BookResponse
import gloss.book.application.dto.BookSummaryResponse
import gloss.book.application.dto.TocReviseRequest
import gloss.book.domain.Book
import gloss.book.domain.BookDraft
import gloss.book.domain.BookRepository
import gloss.book.domain.BookTitle
import gloss.book.domain.Isbn13
import gloss.book.domain.Toc
import gloss.book.domain.TocItem
import gloss.book.domain.TocItemRepository
import gloss.book.domain.TocParser
import gloss.book.domain.TocRepository
import gloss.book.domain.TocText
import gloss.common.error.GlossException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** A reader's books and their contents, each version kept; only the reader who added a book sees it. */
@Service
@Transactional(readOnly = true)
class BookService(
    private val bookRepository: BookRepository,
    private val tocRepository: TocRepository,
    private val tocItemRepository: TocItemRepository,
    private val clock: Clock,
) {
    /** A book typed in by the reader, with its contents as a first draft. */
    @Transactional
    fun create(
        accountId: UUID,
        request: BookCreateRequest,
    ): BookResponse {
        val draft = draftOf(request)
        val text = textOf(request.toc)
        val parsed = parse(text)
        val isbn13 = draft.isbn13
        if (isbn13 != null) {
            bookRepository.lockShelf(accountId)
            if (bookRepository.existsByAccountIdAndIsbn13AndDeletedAtIsNull(accountId, isbn13.value)) {
                throw GlossException(BookError.ISBN_TAKEN)
            }
        }
        val now = clock.instant()
        val book = Book.createManual(accountId = accountId, draft = draft, now = now)
        bookRepository.save(book)
        return newVersion(book = book, text = text, parsed = parsed, now = now)
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

    /** An edit is a new draft version; the one in use stays until the reader confirms another. */
    @Transactional
    fun reviseToc(
        accountId: UUID,
        bookId: UUID,
        request: TocReviseRequest,
    ): BookResponse {
        val text = textOf(request.toc)
        val parsed = parse(text)
        val book = bookRepository.findForUpdate(bookId, accountId) ?: throw GlossException(BookError.BOOK_NOT_FOUND)
        return newVersion(book = book, text = text, parsed = parsed, now = clock.instant())
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
        latest.confirm(clock.instant())
        book.use(latest)
        return BookResponse.from(
            book,
            latest,
            tocItemRepository.findAllByTocIdAndAccountIdOrderByOrd(latest.id, accountId),
        )
    }

    private fun newVersion(
        book: Book,
        text: TocText,
        parsed: List<TocParser.Item>,
        now: Instant,
    ): BookResponse {
        val versionNo = tocRepository.lastVersionNo(book.id, book.accountId) + 1
        val toc = Toc.createManual(book = book, versionNo = versionNo, text = text, now = now)
        tocRepository.save(toc)
        val items = TocItem.itemsOf(toc, parsed, now)
        tocItemRepository.saveAll(items)
        return BookResponse.from(book, toc, items)
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
                authors.size > MAX_AUTHORS || !authors.all(::fits) -> BookError.INVALID_AUTHORS
                publisher != null && !fits(publisher) -> BookError.INVALID_PUBLISHER
                else -> return BookDraft(title = title, authors = authors, publisher = publisher, isbn13 = isbn13)
            }
        throw GlossException(error)
    }

    // Same as book.publisher's check, and the length kept for each author.
    private fun fits(name: String) = name.codePointCount(0, name.length) <= MAX_NAME && name.none { it.isISOControl() }

    private fun textOf(raw: String): TocText = TocText.of(raw) ?: throw GlossException(BookError.INVALID_TOC)

    private fun parse(text: TocText): List<TocParser.Item> {
        val result = TocParser.parse(text.value)
        BookError.of(result)?.let { throw GlossException(it) }
        return (result as TocParser.Result.Parsed).items
    }

    private companion object {
        // An edited volume's contributors fit; a longer list is more likely a paste gone wrong.
        const val MAX_AUTHORS = 20

        // A name with its role, as "마틴 파울러 (지은이)", or a publisher's imprint, with room to spare.
        const val MAX_NAME = 100
    }
}
