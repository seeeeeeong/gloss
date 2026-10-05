package gloss.account.application.dto

import org.springframework.security.web.csrf.CsrfToken

data class CsrfResponse(
    val headerName: String,
    val token: String,
)

fun CsrfToken.toResponse() = CsrfResponse(headerName, token)
