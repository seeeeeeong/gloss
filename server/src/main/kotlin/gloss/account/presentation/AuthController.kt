package gloss.account.presentation

import gloss.account.application.AccountService
import gloss.account.application.dto.CsrfResponse
import gloss.account.application.dto.LoginRequest
import gloss.account.application.dto.MyAccountResponse
import gloss.account.application.dto.toResponse
import gloss.account.presentation.security.SignedInSession
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Sign-in and the CSRF cookie before it. Spring Security answers POST /api/auth/logout. */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val accountService: AccountService,
    private val signedInSession: SignedInSession,
) {
    @GetMapping("/csrf")
    fun csrf(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): CsrfResponse = signedInSession.csrfToken(httpRequest, httpResponse).toResponse()

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MyAccountResponse {
        val signedIn = accountService.login(request, httpRequest.remoteAddr)
        signedInSession.start(signedIn, httpRequest, httpResponse)
        return signedIn.account
    }
}
