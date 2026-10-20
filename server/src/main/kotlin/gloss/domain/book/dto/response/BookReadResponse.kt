package gloss.domain.book.dto.response

import gloss.domain.book.domain.BookRead
import java.time.Instant

/** The item a book opens at. */
data class BookReadResponse(
    val itemKey: String,
    val readAt: Instant,
) {
    companion object {
        fun from(read: BookRead) = BookReadResponse(itemKey = read.itemKey, readAt = read.readAt)
    }
}
