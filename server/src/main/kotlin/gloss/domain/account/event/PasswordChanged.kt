package gloss.domain.account.event

import java.time.Instant
import java.util.UUID

data class PasswordChanged(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
) : AccountEvent
