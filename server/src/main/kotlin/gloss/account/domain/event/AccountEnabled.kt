package gloss.account.domain.event

import java.time.Instant
import java.util.UUID

data class AccountEnabled(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID,
) : AccountEvent
