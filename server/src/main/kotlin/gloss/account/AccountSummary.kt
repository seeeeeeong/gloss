package gloss.account

import java.util.UUID

data class AccountSummary(
    val id: UUID,
    val loginId: String,
    val displayName: String,
    val role: AccountRole,
    val mustChangePassword: Boolean,
)
