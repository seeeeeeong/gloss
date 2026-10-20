package gloss.domain.book.dto.response

import gloss.domain.book.domain.Book
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class BookSummaryResponse(
    val id: UUID,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String?,
    val publishedOn: LocalDate?,
    val kind: String?,
    val source: String,
    val sourceUrl: String?,
    val confirmed: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(book: Book) =
            BookSummaryResponse(
                id = book.id,
                title = book.title,
                authors = book.authors.toList(),
                publisher = book.publisher,
                isbn13 = book.isbn13,
                publishedOn = book.publishedOn,
                kind = book.kind,
                source = book.source.code,
                sourceUrl = book.sourceUrl,
                confirmed = book.currentTocId != null,
                createdAt = book.createdAt,
            )
    }
}
