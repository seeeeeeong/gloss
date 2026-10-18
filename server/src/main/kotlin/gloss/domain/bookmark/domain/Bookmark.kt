package gloss.domain.bookmark.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

/** A phrase a reader kept from an item of a book, or the item itself. Deleted, never edited. */
@Entity
@Immutable
class Bookmark private constructor(
    id: UUID,
    accountId: UUID,
    draft: BookmarkDraft,
    now: Instant,
) : BaseEntity(id) {
    val accountId: UUID = accountId
    val bookId: UUID = draft.bookId
    val itemKey: String = draft.itemKey
    val quote: String? = draft.quote
    val createdAt: Instant = now

    companion object {
        fun create(
            accountId: UUID,
            draft: BookmarkDraft,
            now: Instant,
        ) = Bookmark(id = UuidV7.at(now), accountId = accountId, draft = draft, now = now)
    }
}

/** An item of a book's confirmed contents, and the phrase picked in it; with none, the item itself. */
data class BookmarkDraft(
    val bookId: UUID,
    val itemKey: String,
    val quote: String?,
)
