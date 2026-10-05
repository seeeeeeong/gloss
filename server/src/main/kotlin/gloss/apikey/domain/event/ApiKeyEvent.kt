package gloss.apikey.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Spring Modulith keeps an undelivered event as JSON under its class name, so renaming one or
 * removing a field strands what is still waiting. Never the key, nor its last four.
 */
sealed interface ApiKeyEvent {
    val eventId: UUID
    val occurredAt: Instant
    val accountId: UUID
    val provider: String
}
