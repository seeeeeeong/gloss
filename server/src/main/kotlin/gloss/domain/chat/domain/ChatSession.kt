package gloss.domain.chat.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

/**
 * One of a reader's conversations with the tutor about a book, with a history of its own. Its
 * messages are kept apart, append-only, and go only with it.
 */
@Entity
@Immutable
class ChatSession private constructor(
    id: UUID,
    accountId: UUID,
    bookId: UUID,
    now: Instant,
) : BaseEntity(id) {
    val accountId: UUID = accountId
    val bookId: UUID = bookId
    val createdAt: Instant = now

    companion object {
        fun start(
            accountId: UUID,
            bookId: UUID,
            now: Instant,
        ) = ChatSession(id = UuidV7.at(now), accountId = accountId, bookId = bookId, now = now)
    }
}

/**
 * A conversation as a list shows it: its first question, the item it started on, the items any of
 * its turns came from, when it started and when its last message was kept.
 */
data class ChatSessionSummary(
    val id: UUID,
    val bookId: UUID,
    val firstQuestion: String,
    val itemKey: String?,
    val itemKeys: List<String>,
    val startedAt: Instant,
    val lastActivityAt: Instant,
)
