package gloss.domain.plan.presentation

import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import gloss.domain.plan.dto.response.PlanStatusResponse
import gloss.domain.plan.service.PlanService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/me/books/{bookId}/plan")
class PlanController(
    private val planService: PlanService,
) {
    @GetMapping
    fun status(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): PlanStatusResponse = planService.status(account.id, bookId)

    @PostMapping("/retry")
    fun retry(
        @CurrentAccount account: SessionAccount,
        @PathVariable bookId: UUID,
    ): PlanStatusResponse = planService.retry(account.id, bookId)
}
