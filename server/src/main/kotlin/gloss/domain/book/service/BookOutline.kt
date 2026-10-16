package gloss.domain.book.service

import gloss.domain.book.domain.Book
import gloss.domain.book.domain.TocItem
import java.util.UUID

/** A book and the contents in use, for the modules that write from them. */
data class BookOutline(
    val bookId: UUID,
    val tocId: UUID,
    val title: String,
    val authors: List<String>,
    val items: List<OutlineItem>,
) {
    companion object {
        internal fun of(
            book: Book,
            tocId: UUID,
            items: List<TocItem>,
        ): BookOutline {
            val ords = items.associate { it.id to it.ord }
            return BookOutline(
                bookId = book.id,
                tocId = tocId,
                title = book.title,
                authors = book.authors.toList(),
                items =
                    items.map {
                        OutlineItem(
                            ord = it.ord,
                            parent = it.parentId?.let(ords::getValue),
                            depth = it.depth,
                            number = it.number,
                            title = it.title,
                            itemKey = it.itemKey,
                        )
                    },
            )
        }
    }
}

/** [ord] is the item's place in the whole list, from 0, and [parent] the ord of the item it sits under. */
data class OutlineItem(
    val ord: Int,
    val parent: Int?,
    val depth: Int,
    val number: String?,
    val title: String,
    val itemKey: String,
)
