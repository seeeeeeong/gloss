package gloss.plan.application

import gloss.book.domain.event.TocConfirmed
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class PlanBookListener(
    private val planService: PlanService,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "plan.book")
    fun on(event: TocConfirmed) {
        planService.request(accountId = event.accountId, bookId = event.bookId, tocId = event.tocId)
    }
}
