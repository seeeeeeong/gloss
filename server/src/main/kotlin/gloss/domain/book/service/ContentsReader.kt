package gloss.domain.book.service

import gloss.common.error.GlossException
import gloss.domain.book.domain.TocParser
import gloss.domain.book.domain.TocText

/** Contents read into a tree as a book would keep them, for a module that holds them before any book is made. */
object ContentsReader {
    /** Refused as a book's contents would be, with the same errors. */
    fun read(raw: String): List<OutlineItem> {
        val text = TocText.of(raw) ?: throw GlossException(BookError.INVALID_TOC)
        val result = TocParser.parse(text.value)
        BookError.of(result)?.let { throw GlossException(it) }
        return (result as TocParser.Result.Parsed).items.map {
            OutlineItem(
                ord = it.ord,
                parent = it.parent,
                depth = it.depth,
                number = it.number,
                title = it.title,
                itemKey = it.key,
            )
        }
    }
}
