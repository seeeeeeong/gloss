package gloss.domain.book.dto.response

import gloss.domain.book.domain.BookRead
import java.time.Instant
import java.util.UUID

/** A book read lately, with the item last read in it. */
data class RecentBookResponse(
    val bookId: UUID,
    val title: String,
    val itemKey: String,
    val itemNumber: String?,
    val itemTitle: String,
    val readAt: Instant,
) {
    companion object {
        // A key is the path to its item, so its last part still names an item the contents in use left out.
        fun from(read: BookRead) =
            RecentBookResponse(
                bookId = read.bookId,
                title = read.title,
                itemKey = read.itemKey,
                itemNumber = read.itemNumber,
                itemTitle = read.itemTitle ?: read.itemKey.substringAfterLast(" > "),
                readAt = read.readAt,
            )
    }
}
