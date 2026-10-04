package gloss.account.web

import gloss.account.AccountService
import gloss.account.dto.MeResponse
import gloss.account.dto.PasswordChangeRequest
import gloss.account.security.SessionAccount
import gloss.account.security.SignedInSession
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
