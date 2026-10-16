package gloss.domain.book.dto.response

import gloss.domain.book.domain.SourcedBook
import java.time.LocalDate

/** An edition a search found. [ref] adds it; [url] is its page at the source, shown with it. */
data class BookCandidateResponse(
    val ref: String,
    val source: String,
    val url: String,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String,
    val publishedOn: LocalDate?,
    val kind: String?,
    val hasContents: Boolean,
) {
    companion object {
        fun from(
            book: SourcedBook,
            hasContents: Boolean,
        ) = BookCandidateResponse(
            ref = book.ref,
            source = book.source.code,
            url = book.url,
            title = book.title,
            authors = book.authors,
            publisher = book.publisher,
            isbn13 = book.isbn13.value,
            publishedOn = book.publishedOn,
            kind = book.kind,
            hasContents = hasContents,
        )
    }
}
