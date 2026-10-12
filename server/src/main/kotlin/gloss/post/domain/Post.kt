package gloss.post.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

/** The article for one section of a book. Its versions are kept; a reader sees the current one. */
@Entity
@DynamicUpdate
class Post private constructor(
    id: UUID,
    accountId: UUID,
    bookId: UUID,
    itemKey: String,
    now: Instant,
) : BaseEntity(id) {
    var accountId: UUID = accountId
        protected set
    var bookId: UUID = bookId
        protected set
    var itemKey: String = itemKey
        protected set
    var currentVersionId: UUID? = null
        protected set
    var stale: Boolean = false
        protected set
    var createdAt: Instant = now
        protected set

    /** A new version is written from the plan in use, so the post is no longer stale. */
    fun show(version: PostVersion) {
        check(version.postId == id) { "Only this post's own version" }
        currentVersionId = version.id
        stale = false
    }

    companion object {
        // Same as post.item_key's check, and toc_item's.
        const val MAX_ITEM_KEY = 600

        fun create(
            accountId: UUID,
            bookId: UUID,
            itemKey: String,
            now: Instant,
        ) = Post(id = UuidV7.at(now), accountId = accountId, bookId = bookId, itemKey = itemKey, now = now)
    }
}
