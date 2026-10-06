package gloss.budget.presentation

import gloss.budget.application.BudgetService
import gloss.budget.application.dto.LimitRequest
import gloss.budget.application.dto.UsageResponse
import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/usage")
class UsageController(
    private val budgetService: BudgetService,
) {
    @GetMapping
    fun usage(
        @CurrentAccount account: SessionAccount,
    ): UsageResponse = budgetService.usage(account.id)

    @PutMapping("/limit")
    fun setLimit(
        @CurrentAccount account: SessionAccount,
        @RequestBody request: LimitRequest,
    ): UsageResponse = budgetService.setLimit(account.id, request)
}
