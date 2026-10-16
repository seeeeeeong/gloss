package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.BookDraft
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.BookSourceGateway
import gloss.domain.book.domain.BookTitle
import gloss.domain.book.domain.SourcedBook
import gloss.domain.book.domain.TocParser
import gloss.domain.book.domain.TocText
import gloss.domain.book.dto.request.BookSourceAddRequest
import gloss.domain.book.dto.response.BookCandidateResponse
import gloss.domain.book.dto.response.BookResponse
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Finds a book in the source and adds it with the contents the source gives. The source is asked
 * before any transaction opens, so a slow answer holds no connection.
 */
@Service
class BookSourceService(
    private val bookSourceGateway: BookSourceGateway,
    private val bookSourceThrottle: BookSourceThrottle,
    private val bookService: BookService,
) {
    fun search(
        accountId: UUID,
        query: String,
    ): List<BookCandidateResponse> {
        val words = query.trim()
        val length = words.codePointCount(0, words.length)
        if (length !in 1..MAX_QUERY || words.any { it.isISOControl() }) throw GlossException(BookError.INVALID_QUERY)
        val found = ask(accountId) { bookSourceGateway.search(words) }
        return found.map { BookCandidateResponse.from(it, hasContents = readable(it.contents)) }
    }

    /**
     * The book is read from the source again by its reference: what a browser says about it is not
     * kept. With contents of the reader's own, as for an edition the source has none for, those are
     * the first draft and the book still names its source.
     */
    fun add(
        accountId: UUID,
        request: BookSourceAddRequest,
    ): BookResponse {
        val sourced =
            ask(accountId) { bookSourceGateway.find(request.ref) }
                ?: throw GlossException(BookError.SOURCE_BOOK_NOT_FOUND)
        val typed = request.toc?.takeIf { it.isNotBlank() }
        if (typed == null && !readable(sourced.contents)) throw GlossException(BookError.SOURCE_NO_TOC)
        return bookService.add(accountId = accountId, draft = draftOf(sourced), typed = typed, sourced = sourced)
    }

    private fun <T> ask(
        accountId: UUID,
        call: () -> T,
    ): T {
        val refused =
            when (bookSourceThrottle.ask(accountId)) {
                BookSourceThrottle.Verdict.ALLOWED -> null
                BookSourceThrottle.Verdict.READER_TOO_OFTEN -> BookError.SEARCH_TOO_OFTEN
                BookSourceThrottle.Verdict.KEY_SPENT -> BookError.SOURCE_BUSY
            }
        if (refused != null) throw GlossException(refused)
        try {
            return call()
        } catch (e: BookSourceFailed) {
            throw GlossException(BookError.of(e.kind), e)
        }
    }

    // Contents the reader could not fix by editing, too long or too deep, count as none: they type their own.
    private fun readable(contents: String?): Boolean =
        contents?.let(TocText::of)?.let { TocParser.parse(it.value) is TocParser.Result.Parsed } ?: false

    private fun draftOf(sourced: SourcedBook): BookDraft {
        val title = BookTitle.of(sourced.title) ?: throw GlossException(BookError.SOURCE_FAILED)
        return BookDraft(
            title = title,
            authors = sourced.authors,
            publisher = sourced.publisher,
            isbn13 = sourced.isbn13,
        )
    }

    private companion object {
        // A long title with its subtitle fits; longer is not a search.
        const val MAX_QUERY = 100
    }
}
