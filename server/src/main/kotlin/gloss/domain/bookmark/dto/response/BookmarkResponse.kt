package gloss.domain.bookmark.dto.response

import gloss.domain.bookmark.domain.Bookmark
import java.time.Instant
import java.util.UUID

/** A bookmark with where it is: the book's title, and the item's number and title in the contents in use. */
data class BookmarkResponse(
    val id: UUID,
    val bookId: UUID,
    val bookTitle: String,
    val itemKey: String,
    val itemNumber: String?,
    val itemTitle: String,
    val quote: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            bookmark: Bookmark,
            bookTitle: String,
            itemNumber: String?,
            itemTitle: String,
        ) = BookmarkResponse(
            id = bookmark.id,
            bookId = bookmark.bookId,
            bookTitle = bookTitle,
            itemKey = bookmark.itemKey,
            itemNumber = itemNumber,
            itemTitle = itemTitle,
            quote = bookmark.quote,
            createdAt = bookmark.createdAt,
        )
    }
}
