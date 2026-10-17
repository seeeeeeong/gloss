package gloss.domain.book.service

import java.time.Instant
import java.util.UUID

/** The version of a book's contents in use, and when the reader confirmed it. */
data class ContentsInUse(
    val tocId: UUID,
    val confirmedAt: Instant,
)
