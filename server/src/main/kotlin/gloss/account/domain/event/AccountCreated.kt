package gloss.account.domain.event

import java.time.Instant
import java.util.UUID

/** [operatorId] is null for the first operator, made at startup. */
data class AccountCreated(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID?,
    val role: String,
) : AccountEvent
