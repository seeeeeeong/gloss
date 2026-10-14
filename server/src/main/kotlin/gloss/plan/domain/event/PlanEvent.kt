package gloss.plan.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Spring Modulith keeps an undelivered event as JSON under its class name, so renaming one or
 * removing a field strands what is still waiting. Ids only.
 */
sealed interface PlanEvent {
    val eventId: UUID
    val occurredAt: Instant
}

/** A book has a new plan: what was written from an earlier one is no longer of the contents in use. */
data class PlanKept(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val bookId: UUID,
    val planId: UUID,
) : PlanEvent
