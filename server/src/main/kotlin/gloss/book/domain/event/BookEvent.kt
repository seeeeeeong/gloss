package gloss.book.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Spring Modulith keeps an undelivered event as JSON under its class name, so renaming one or
 * removing a field strands what is still waiting. Ids only.
 */
sealed interface BookEvent {
    val eventId: UUID
    val occurredAt: Instant
}

/** A reader confirmed a version of a book's contents: from now, plans and posts are made from it. */
data class TocConfirmed(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val bookId: UUID,
    val tocId: UUID,
) : BookEvent
