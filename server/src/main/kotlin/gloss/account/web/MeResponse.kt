package gloss.account.web

import gloss.account.AccountSummary
import java.util.UUID

data class MeResponse(
    val id: UUID,
    val loginId: String,
    val displayName: String,
    val role: String,
    val mustChangePassword: Boolean,
    val csrf: CsrfResponse,
)

fun AccountSummary.toResponse(csrf: CsrfResponse) =
    MeResponse(id, loginId, displayName, role.code, mustChangePassword, csrf)
