package gloss.domain.generation.service

import gloss.domain.account.domain.event.AccountDisabled
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class GenerationAccountListener(
    private val generationService: GenerationService,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "generation.account")
    fun on(event: AccountDisabled) {
        generationService.cancelForDisabledAccount(event.accountId)
    }
}
