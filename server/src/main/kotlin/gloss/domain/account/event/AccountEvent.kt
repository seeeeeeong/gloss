package gloss.domain.account.event

import java.time.Instant
import java.util.UUID

/**
 * Spring Modulith keeps an undelivered event as JSON under its class name, so renaming one or
 * removing a field strands what is still waiting. Ids only: no password, no typed login id.
 */
sealed interface AccountEvent {
    val eventId: UUID
    val occurredAt: Instant
}
