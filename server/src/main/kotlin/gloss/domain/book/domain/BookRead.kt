package gloss.domain.book.domain

import java.time.Instant
import java.util.UUID

/**
 * The item a reader last read in a book, with the book's title, and the item's number and title in
 * the contents in use; both null when those contents no longer hold it.
 */
data class BookRead(
    val bookId: UUID,
    val title: String,
    val itemKey: String,
    val itemNumber: String?,
    val itemTitle: String?,
    val readAt: Instant,
)
