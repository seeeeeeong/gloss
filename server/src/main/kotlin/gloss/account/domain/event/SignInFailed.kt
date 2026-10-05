package gloss.account.domain.event

import java.time.Instant
import java.util.UUID

/** [accountId] is null when no account has the login id. */
data class SignInFailed(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID?,
    val ip: String,
    val reason: SignInFailure,
) : AccountEvent
