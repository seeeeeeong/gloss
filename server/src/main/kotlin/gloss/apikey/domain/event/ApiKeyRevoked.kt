package gloss.apikey.domain.event

import java.time.Instant
import java.util.UUID

data class ApiKeyRevoked(
    override val eventId: UUID,
    override val occurredAt: Instant,
    override val accountId: UUID,
    override val provider: String,
    val reason: KeyRevocation,
) : ApiKeyEvent
