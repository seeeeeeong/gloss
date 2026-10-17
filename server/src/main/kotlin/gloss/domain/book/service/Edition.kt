package gloss.domain.book.service

import gloss.domain.book.domain.SourcedBook
import java.time.LocalDate

/** An edition a search found, for a module that shows and keeps it. [ref] adds it; [url] is its page at the source. */
data class Edition(
    val ref: String,
    val source: String,
    val url: String,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String,
    val publishedOn: LocalDate?,
    val kind: String?,
    val cover: String?,
    val hasContents: Boolean,
) {
    companion object {
        internal fun of(
            book: SourcedBook,
            hasContents: Boolean,
        ) = Edition(
            ref = book.ref,
            source = book.source.code,
            url = book.url,
            title = book.title,
            authors = book.authors,
            publisher = book.publisher,
            isbn13 = book.isbn13.value,
            publishedOn = book.publishedOn,
            kind = book.kind,
            cover = book.cover,
            hasContents = hasContents,
        )
    }
}

/** Where a source shows a book: its name and the page its terms ask to link wherever its details are. */
data class SourcePage(
    val source: String,
    val url: String,
)

/**
 * A book read from its source, for a caller to add with [BookSourceService.keep] once it is ready to.
 * [contents] are the source's, one item a line, null when it has none a reader could use.
 */
class SourcedEdition internal constructor(
    internal val book: SourcedBook,
    val contents: String?,
) {
    val ref: String get() = book.ref

    val title: String get() = book.title

    val authors: List<String> get() = book.authors

    val publisher: String? get() = book.publisher

    val publishedOn: LocalDate? get() = book.publishedOn
}
