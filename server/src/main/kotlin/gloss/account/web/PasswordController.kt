package gloss.account.web

import gloss.account.AccountService
import gloss.account.PasswordChangeResult
import gloss.account.PasswordDecision
import gloss.account.security.SessionAccount
import gloss.account.security.SignedInSession
import gloss.common.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PasswordController(
    private val accounts: AccountService,
    private val session: SignedInSession,
) {
    @PutMapping("/api/me/password")
    fun change(
        @AuthenticationPrincipal principal: SessionAccount,
        @Valid @RequestBody body: PasswordChangeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> =
        when (val result = accounts.changePassword(principal.id, body.currentPassword, body.newPassword)) {
            is PasswordChangeResult.Changed -> {
                // The epoch moved, which ends every other session; this one carries the new value.
                session.remember(principal.copy(sessionEpoch = result.sessionEpoch), request, response)
                ResponseEntity.noContent().build()
            }

            PasswordChangeResult.SameAsCurrent -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiError("password_unchanged:account", "Choose a password other than the current one"))
            }

            PasswordChangeResult.WrongPassword -> {
                ResponseEntity.badRequest().body(ApiError("wrong_password:account", "The current password is wrong"))
            }

            is PasswordChangeResult.Rejected -> {
                ResponseEntity.badRequest().body(result.decision.toError())
            }
        }

    private fun PasswordDecision.toError() =
        when (this) {
            PasswordDecision.TooShort -> ApiError("password_too_short:account", "Use at least 15 characters")
            PasswordDecision.TooLong -> ApiError("password_too_long:account", "Use at most 72 bytes")
            PasswordDecision.Blank -> ApiError("password_blank:account", "Use more than whitespace")
            PasswordDecision.Accepted -> error("An accepted password is not an error")
        }
}
