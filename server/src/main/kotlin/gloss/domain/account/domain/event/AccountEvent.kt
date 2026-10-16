package gloss.domain.account.domain.event

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

/** [operatorId] is null for the first operator, made at startup. */
data class AccountCreated(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID?,
    val role: String,
) : AccountEvent

data class AccountDisabled(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID,
) : AccountEvent

data class AccountEnabled(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID,
) : AccountEvent

data class PasswordChanged(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
) : AccountEvent

data class PasswordReset(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val operatorId: UUID,
) : AccountEvent

data class SignedIn(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID,
    val ip: String,
) : AccountEvent

/** [accountId] is null when no account has the login id. */
data class SignInFailed(
    override val eventId: UUID,
    override val occurredAt: Instant,
    val accountId: UUID?,
    val ip: String,
    val reason: SignInFailure,
) : AccountEvent

enum class SignInFailure {
    UNKNOWN_LOGIN_ID,
    WRONG_PASSWORD,
    DISABLED,
}
