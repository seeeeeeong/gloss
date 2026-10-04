package gloss.domain.account.controller

import gloss.domain.account.dto.AccountCreateRequest
import gloss.domain.account.dto.AdminAccountResponse
import gloss.domain.account.dto.CreatedAccountResponse
import gloss.domain.account.dto.TemporaryPasswordResponse
import gloss.domain.account.security.SessionAccount
import gloss.domain.account.service.AdminAccountService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @AuthenticationPrincipal operator: SessionAccount,
        @Valid @RequestBody request: AccountCreateRequest,
    ): CreatedAccountResponse = adminAccountService.create(operator.id, request)

    @PostMapping("/{id}/password-reset")
    fun resetPassword(
        @PathVariable id: UUID,
    ): TemporaryPasswordResponse = adminAccountService.resetPassword(id)

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disable(
        @PathVariable id: UUID,
    ) {
        adminAccountService.disable(id)
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun enable(
        @PathVariable id: UUID,
    ) {
        adminAccountService.enable(id)
    }
}
