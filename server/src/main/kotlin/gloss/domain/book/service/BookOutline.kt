package gloss.domain.book.service

import gloss.domain.book.domain.Book
import gloss.domain.book.domain.ReadablePolicy
import gloss.domain.book.domain.TocItem
import gloss.domain.book.domain.style.WritingStyle
import java.util.UUID

/** A book and the contents in use, for the modules that write from them. */
data class BookOutline(
    val bookId: UUID,
    val tocId: UUID,
    val title: String,
    val authors: List<String>,
    val style: WritingStyle,
    val items: List<OutlineItem>,
) {
    /** The items the book is read in, as the reader's contents show them: front and back matter left out. */
    val readableItems: List<OutlineItem> get() {
        val places = items.withIndex().associate { (place, item) -> item.ord to place }
        val entries = items.map { ReadablePolicy.Entry(parent = it.parent?.let(places::get), number = it.number) }
        return ReadablePolicy.readable(entries).map(items::get)
    }

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
                style = book.style,
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
