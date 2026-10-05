package gloss.account.domain.event

import java.time.Instant
import java.util.UUID

data class SignedIn(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val ip: String,
) : AccountEvent
