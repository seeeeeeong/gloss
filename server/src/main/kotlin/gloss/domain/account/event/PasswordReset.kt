package gloss.domain.account.event

import java.time.Instant
import java.util.UUID

data class PasswordReset(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID,
) : AccountEvent
