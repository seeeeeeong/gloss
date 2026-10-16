package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.BookDraft
import gloss.domain.book.domain.BookTitle

/**
 * A book a reader typed in, for a module that holds it before [BookService.createConfirmed] makes it.
 * Only [of] makes one, checked as a typed book is, so what is held can always be made.
 */
class TypedBook private constructor(
    val title: String,
    val authors: List<String>,
    val toc: String,
) {
    companion object {
        fun of(
            title: String,
            authors: List<String>,
            toc: String,
        ): TypedBook {
            val checked = BookTitle.of(title) ?: throw GlossException(BookError.INVALID_TITLE)
            val names = authors.map(String::trim).filter(String::isNotEmpty)
            if (names.size > BookDraft.MAX_AUTHORS || !names.all(BookDraft::fits)) {
                throw GlossException(BookError.INVALID_AUTHORS)
            }
            ContentsReader.read(toc)
            return TypedBook(checked.value, names, toc)
        }
    }
}
