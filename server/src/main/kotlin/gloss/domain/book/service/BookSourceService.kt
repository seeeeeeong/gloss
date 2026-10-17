package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.BookDraft
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.BookSourceGateway
import gloss.domain.book.domain.BookTitle
import gloss.domain.book.domain.SourcedBook
import gloss.domain.book.domain.TocParser
import gloss.domain.book.domain.TocText
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.dto.request.BookSourceAddRequest
import gloss.domain.book.dto.request.TocReviseRequest
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
    private val tocService: TocService,
    private val editionCache: EditionCache,
) {
    fun search(
        accountId: UUID,
        query: String,
    ): List<BookCandidateResponse> {
        val words = checked(query)
        admit(bookSourceThrottle.ask(accountId))
        return answer { bookSourceGateway.search(words) }
            .map { BookCandidateResponse.from(it, hasContents = readable(it.contents)) }
    }

    /**
     * The same search, for a module that keeps only the words and the refs it was shown: asked again
     * to show them, it answers from memory for a while instead of asking the source, and the same
     * words asked at once are asked of the source once. Each reader is held to their own pace before
     * waiting on anyone's ask, so only the source's own failure is shared, never another's limit; only
     * the ask that reaches the source spends the key's.
     */
    fun editions(
        accountId: UUID,
        query: String,
    ): List<Edition> {
        val words = checked(query)
        return editionCache.get(words, admit = { admit(bookSourceThrottle.reader(accountId)) }) {
            admit(bookSourceThrottle.source())
            answer { bookSourceGateway.search(words) }.map { Edition.of(it, hasContents = readable(it.contents)) }
        }
    }

    /** Known from the ref alone, so it is shown even when the source cannot be asked. */
    fun pageOf(ref: String): SourcePage =
        SourcePage(source = bookSourceGateway.source.code, url = bookSourceGateway.page(ref))

    /**
     * The book is read from the source again by its reference: what a browser says about it is not
     * kept. With contents of the reader's own, as for an edition the source has none for, those are
     * the first draft and the book still names its source.
     */
    fun add(
        accountId: UUID,
        request: BookSourceAddRequest,
    ): BookResponse {
        admit(bookSourceThrottle.ask(accountId))
        val sourced =
            answer { bookSourceGateway.find(request.ref) } ?: throw GlossException(BookError.SOURCE_BOOK_NOT_FOUND)
        val typed = request.toc?.takeIf { it.isNotBlank() }
        if (typed == null && !readable(sourced.contents)) throw GlossException(BookError.SOURCE_NO_TOC)
        return bookService.add(
            accountId = accountId,
            draft = draftOf(sourced, WritingStyle.DEFAULT),
            typed = typed,
            sourced = sourced,
        )
    }

    /**
     * The edition [ref] names, read from the source again: for a caller that adds it with [keep] in a
     * transaction of its own, which this call must stay out of.
     */
    fun fetch(
        accountId: UUID,
        ref: String,
    ): SourcedEdition {
        admit(bookSourceThrottle.ask(accountId))
        val sourced = answer { bookSourceGateway.find(ref) } ?: throw GlossException(BookError.SOURCE_BOOK_NOT_FOUND)
        return SourcedEdition(sourced, contents = sourced.contents?.takeIf(::readable))
    }

    /**
     * Adds what [fetch] read and confirms its contents, in the caller's transaction. The source's
     * contents are the first version and [contents] the reader changed a second, so each keeps where
     * it came from; for an edition the source has none for, the reader's are the first. The one
     * confirmed is the last. The book's posts are written in [style].
     */
    fun keep(
        accountId: UUID,
        edition: SourcedEdition,
        contents: String,
        style: WritingStyle,
    ): ContentsVersion {
        val source = edition.contents
        val added =
            bookService.add(
                accountId = accountId,
                draft = draftOf(edition.book, style),
                typed = if (source == null) contents else null,
                sourced = edition.book,
            )
        val book =
            if (source == null || contents == source) {
                added
            } else {
                bookService.reviseToc(accountId, added.id, TocReviseRequest(contents))
            }
        return tocService.confirmContents(accountId, book.id, book.toc.id)
    }

    private fun checked(query: String): String {
        val words = query.trim()
        val length = words.codePointCount(0, words.length)
        if (length !in 1..MAX_QUERY || words.any { it.isISOControl() }) throw GlossException(BookError.INVALID_QUERY)
        return words
    }

    private fun admit(verdict: BookSourceThrottle.Verdict) {
        val refused =
            when (verdict) {
                BookSourceThrottle.Verdict.ALLOWED -> null
                BookSourceThrottle.Verdict.READER_TOO_OFTEN -> BookError.SEARCH_TOO_OFTEN
                BookSourceThrottle.Verdict.KEY_SPENT -> BookError.SOURCE_BUSY
            }
        if (refused != null) throw GlossException(refused)
    }

    private fun <T> answer(call: () -> T): T {
        try {
            return call()
        } catch (e: BookSourceFailed) {
            throw GlossException(BookError.of(e.kind), e)
        }
    }

    // Contents the reader could not fix by editing, too long or too deep, count as none: they type their own.
    private fun readable(contents: String?): Boolean =
        contents?.let(TocText::of)?.let { TocParser.parse(it.value) is TocParser.Result.Parsed } ?: false

    private fun draftOf(
        sourced: SourcedBook,
        style: WritingStyle,
    ): BookDraft {
        val title = BookTitle.of(sourced.title) ?: throw GlossException(BookError.SOURCE_FAILED)
        return BookDraft(
            title = title,
            authors = sourced.authors,
            publisher = sourced.publisher,
            isbn13 = sourced.isbn13,
            style = style,
        )
    }

    private companion object {
        // A long title with its subtitle fits; longer is not a search.
        const val MAX_QUERY = 100
    }
}
