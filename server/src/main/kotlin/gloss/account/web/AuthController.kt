package gloss.account.web

import gloss.account.AccountService
import gloss.account.LoginResult
import gloss.account.security.SessionAccount
import gloss.account.security.SignedInSession
import gloss.common.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val accounts: AccountService,
    private val session: SignedInSession,
) {
    @GetMapping("/csrf")
    fun csrf(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = session.csrfToken(request, response).toResponse()

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody body: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        when (val result = accounts.authenticate(body.loginId, body.password)) {
            LoginResult.Failed -> {
                ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiError("login_failed:account", "The login id or password is wrong"))
            }

            is LoginResult.Succeeded -> {
                val principal = SessionAccount(result.account.id, result.account.role, result.sessionEpoch)
                val csrf = session.start(principal, request, response)
                ResponseEntity.ok(result.account.toResponse(csrf.toResponse()))
            }
        }

    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: SessionAccount,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): MeResponse = accounts.get(principal.id).toResponse(session.csrfToken(request, response).toResponse())
}
