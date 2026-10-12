package gloss.book.domain

import java.time.LocalDate

/**
 * A catalogue outside Gloss that knows books and, for some, their contents. Called on the operator's
 * key, so every reader's searches spend the same allowance.
 */
interface BookSourceGateway {
    /** The books a title or a few words find, best match first. */
    fun search(query: String): List<SourcedBook>

    /** Null when the source has no such book. [ref] is a [SourcedBook.ref] a search gave. */
    fun find(ref: String): SourcedBook?
}

/**
 * A book as a source describes it, each part cut to fit a book's columns. It has an ISBN: what a
 * shop sells without one is a set, a record or goods. [contents] is one item a line with the source's
 * markup taken off, null when the source has none.
 */
data class SourcedBook(
    val source: BookSource,
    val ref: String,
    val url: String,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: Isbn13,
    val publishedOn: LocalDate?,
    val kind: String?,
    val contents: String?,
)

/** A call to the source that gave no answer to use. */
class BookSourceFailed(
    val kind: Kind,
) : RuntimeException(kind.name) {
    enum class Kind {
        /** No key is set, or the source refused it: nothing a reader can wait out. */
        UNAVAILABLE,

        /** The source's limit for the key is used up for now. */
        BUSY,

        /** No answer, an answer that could not be read, or the source's own failure. */
        FAILED,
    }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
