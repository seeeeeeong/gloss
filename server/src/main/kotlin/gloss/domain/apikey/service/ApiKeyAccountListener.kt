package gloss.domain.apikey.service

import gloss.domain.account.domain.event.AccountDisabled
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class ApiKeyAccountListener(
    private val apiKeyService: ApiKeyService,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "apikey.account")
    fun on(event: AccountDisabled) {
        apiKeyService.revokeForDisabledAccount(event.accountId)
    }
}
