package gloss.account.presentation

import gloss.account.application.AccountService
import gloss.account.application.dto.MyAccountResponse
import gloss.account.application.dto.PasswordChangeRequest
import gloss.account.application.dto.ProfileUpdateRequest
import gloss.account.presentation.security.SignedInSession
import gloss.common.security.CurrentAccount
import gloss.common.security.SessionAccount
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The signed-in account. Other modules answer their own paths under /api/me. */
@RestController
@RequestMapping("/api/me")
class MyAccountController(
    private val accountService: AccountService,
    private val signedInSession: SignedInSession,
) {
    @GetMapping
    fun me(
        @CurrentAccount account: SessionAccount,
    ): MyAccountResponse = accountService.getMyAccount(account.id)

    @PutMapping
    fun updateProfile(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: ProfileUpdateRequest,
    ): MyAccountResponse = accountService.updateProfile(account.id, request)

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @CurrentAccount account: SessionAccount,
        @Valid @RequestBody request: PasswordChangeRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        val sessionEpoch = accountService.changePassword(account.id, request)
        signedInSession.remember(account.copy(sessionEpoch = sessionEpoch), httpRequest, httpResponse)
    }
}
