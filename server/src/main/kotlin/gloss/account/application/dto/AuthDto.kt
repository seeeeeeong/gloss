package gloss.account.application.dto

import gloss.account.domain.AccountRole
import jakarta.validation.constraints.NotBlank
import org.springframework.security.web.csrf.CsrfToken

data class LoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
)

/** What sign-in returns: the response, and what the session needs to remember. */
data class SignedInAccount(
    val account: MyAccountResponse,
    val role: AccountRole,
    val sessionEpoch: Int,
)

data class CsrfResponse(
    val headerName: String,
    val token: String,
)

fun CsrfToken.toResponse() = CsrfResponse(headerName, token)
