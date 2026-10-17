package gloss.domain.book.dto.response

import gloss.domain.book.domain.Book
import gloss.domain.book.domain.Toc
import gloss.domain.book.domain.TocItem
import java.util.UUID

/** The book with its latest contents, which may be a draft newer than the one in use. */
data class BookResponse(
    val id: UUID,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String?,
    val source: String,
    val sourceUrl: String?,
    val currentTocId: UUID?,
    val level: String,
    val voice: String,
    val toc: TocResponse,
) {
    companion object {
        fun from(
            book: Book,
            toc: Toc,
            items: List<TocItem>,
        ) = BookResponse(
            id = book.id,
            title = book.title,
            authors = book.authors.toList(),
            publisher = book.publisher,
            isbn13 = book.isbn13,
            source = book.source.code,
            sourceUrl = book.sourceUrl,
            currentTocId = book.currentTocId,
            level = book.level.code,
            voice = book.voice.code,
            toc = TocResponse.from(toc, items),
        )
    }
}
