package gloss.post.application

import gloss.plan.domain.event.PlanKept
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class PostPlanListener(
    private val postGenerationService: PostGenerationService,
) {
    // The id names this listener's undelivered events in event_publication; keep it when renaming.
    @ApplicationModuleListener(id = "post.plan")
    fun on(event: PlanKept) {
        postGenerationService.outdatePosts(accountId = event.accountId, bookId = event.bookId, planId = event.planId)
    }
}
