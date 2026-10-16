package gloss.domain.book.service

import java.util.UUID

/** A version of a book's contents, for a module that keeps which one a reader was shown or confirmed. */
data class ContentsVersion(
    val bookId: UUID,
    val tocId: UUID,
    val versionNo: Int,
    val items: Int,
)
