package gloss.domain.account.controller

import gloss.domain.account.dto.MeResponse
import gloss.domain.account.dto.PasswordChangeRequest
import gloss.domain.account.dto.ProfileUpdateRequest
import gloss.domain.account.security.SessionAccount
import gloss.domain.account.security.SignedInSession
import gloss.domain.account.service.AccountService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The signed-in account. Other modules answer their own paths under /api/me. */
@RestController
@RequestMapping("/api/me")
class MeController(
    private val accountService: AccountService,
    private val signedInSession: SignedInSession,
) {
    @GetMapping
    fun me(
        @AuthenticationPrincipal account: SessionAccount,
    ): MeResponse = accountService.getMe(account.id)

    @PutMapping
    fun updateProfile(
        @AuthenticationPrincipal account: SessionAccount,
        @Valid @RequestBody request: ProfileUpdateRequest,
    ): MeResponse = accountService.updateProfile(account.id, request)

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @AuthenticationPrincipal account: SessionAccount,
        @Valid @RequestBody request: PasswordChangeRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        val sessionEpoch = accountService.changePassword(account.id, request)
        signedInSession.remember(account.copy(sessionEpoch = sessionEpoch), httpRequest, httpResponse)
    }
}
