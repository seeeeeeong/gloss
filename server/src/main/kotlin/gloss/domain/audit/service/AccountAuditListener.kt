package gloss.domain.audit.service

import gloss.domain.account.event.AccountCreated
import gloss.domain.account.event.AccountDisabled
import gloss.domain.account.event.AccountEnabled
import gloss.domain.account.event.AccountEvent
import gloss.domain.account.event.PasswordChanged
import gloss.domain.account.event.PasswordReset
import gloss.domain.account.event.SignInFailed
import gloss.domain.account.event.SignedIn
import gloss.domain.audit.repository.JdbcAuditEvents
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class AccountAuditListener(
    private val jdbcAuditEvents: JdbcAuditEvents,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "audit.account")
    fun on(event: AccountEvent) {
        jdbcAuditEvents.append(entryOf(event))
    }

    private fun entryOf(event: AccountEvent): JdbcAuditEvents.Entry {
        val (kind, actorId, subjectId) =
            when (event) {
                is SignedIn -> Triple("signed_in", event.accountId, event.accountId)
                is SignInFailed -> Triple("sign_in_failed", null, event.accountId)
                is PasswordChanged -> Triple("password_changed", event.accountId, event.accountId)
                is PasswordReset -> Triple("password_reset", event.operatorId, event.accountId)
                is AccountCreated -> Triple("account_created", event.operatorId, event.accountId)
                is AccountDisabled -> Triple("account_disabled", event.operatorId, event.accountId)
                is AccountEnabled -> Triple("account_enabled", event.operatorId, event.accountId)
            }
        // An allow list: a field is recorded only when named here.
        val detail =
            when (event) {
                is SignedIn -> mapOf("ip" to event.ip)
                is SignInFailed -> mapOf("ip" to event.ip, "reason" to event.reason.name.lowercase())
                is AccountCreated -> mapOf("role" to event.role)
                else -> emptyMap()
            }
        return JdbcAuditEvents.Entry(
            eventId = event.eventId,
            occurredAt = event.occurredAt,
            actorId = actorId,
            subjectId = subjectId,
            kind = kind,
            detail = detail,
        )
    }
}
