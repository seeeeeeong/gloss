package gloss.domain.apikey.domain.event

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

data class ApiKeySaved(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val accountId: UUID,
    override val provider: String,
) : ApiKeyEvent

data class ApiKeyRevoked(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val accountId: UUID,
    override val provider: String,
    val reason: KeyRevocation,
) : ApiKeyEvent

enum class KeyRevocation {
    READER,
    REPLACED,
    ACCOUNT_DISABLED,
}
