package gloss.domain.chat.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

/** A reader's one conversation with the tutor about a book. Its messages are kept apart, append-only. */
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
