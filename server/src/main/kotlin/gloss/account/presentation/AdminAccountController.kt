package gloss.account.presentation

import gloss.account.application.AdminAccountService
import gloss.account.application.dto.AccountCreateRequest
import gloss.account.application.dto.AdminAccountResponse
import gloss.account.application.dto.CreatedAccountResponse
import gloss.account.application.dto.TemporaryPasswordResponse
import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Operators only: SecurityConfig refuses /api/admin to anyone else. */
@RestController
@RequestMapping("/api/admin/accounts")
class AdminAccountController(
    private val adminAccountService: AdminAccountService,
) {
    @GetMapping
    fun list(): List<AdminAccountResponse> = adminAccountService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentAccount operator: SessionAccount,
        @Valid @RequestBody request: AccountCreateRequest,
    ): CreatedAccountResponse = adminAccountService.create(operator.id, request)

    @PostMapping("/{id}/password-reset")
    fun resetPassword(
        @CurrentAccount operator: SessionAccount,
        @PathVariable id: UUID,
    ): TemporaryPasswordResponse = adminAccountService.resetPassword(operator.id, id)

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disable(
        @CurrentAccount operator: SessionAccount,
        @PathVariable id: UUID,
    ) {
        adminAccountService.disable(operator.id, id)
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun enable(
        @CurrentAccount operator: SessionAccount,
        @PathVariable id: UUID,
    ) {
        adminAccountService.enable(operator.id, id)
    }
}
