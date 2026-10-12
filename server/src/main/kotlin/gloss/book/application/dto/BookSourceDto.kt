package gloss.book.application.dto

import gloss.book.domain.SourcedBook
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

/** [toc] is the reader's own contents for the edition; without it the source's are used. */
data class BookSourceAddRequest(
    @field:NotBlank val ref: String,
    val toc: String? = null,
)

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
