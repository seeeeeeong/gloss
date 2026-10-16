package gloss.domain.audit.service

import gloss.domain.apikey.domain.event.ApiKeyEvent
import gloss.domain.apikey.domain.event.ApiKeyRevoked
import gloss.domain.apikey.domain.event.ApiKeySaved
import gloss.domain.apikey.domain.event.KeyRevocation
import gloss.domain.audit.infrastructure.JdbcAuditEvents
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class ApiKeyAuditListener(
    private val jdbcAuditEvents: JdbcAuditEvents,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "audit.apikey")
    fun on(event: ApiKeyEvent) {
        val (kind, detail) =
            when (event) {
                is ApiKeySaved -> {
                    "api_key_saved" to mapOf("provider" to event.provider)
                }

                is ApiKeyRevoked -> {
                    "api_key_revoked" to mapOf("provider" to event.provider, "reason" to event.reason.name.lowercase())
                }
            }
        // Revoked for a disabled account, no reader acted.
        val actorId =
            event.accountId.takeUnless {
                event is ApiKeyRevoked &&
                    event.reason == KeyRevocation.ACCOUNT_DISABLED
            }
        jdbcAuditEvents.append(
            JdbcAuditEvents.Entry(
                eventId = event.eventId,
                occurredAt = event.occurredAt,
                actorId = actorId,
                subjectId = event.accountId,
                kind = kind,
                detail = detail,
            ),
        )
    }
}
