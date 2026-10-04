package gloss.domain.account.dto

import gloss.domain.account.entity.Account
import java.time.Instant
import java.util.UUID

data class AdminAccountResponse(
    val id: UUID,
    val loginId: String,
    val displayName: String,
    val role: String,
    val status: String,
    val mustChangePassword: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
) {
    companion object {
        fun from(account: Account) =
            AdminAccountResponse(
                id = account.id,
                loginId = account.loginId,
                displayName = account.displayName,
                role = account.role.code,
                status = account.status.code,
                mustChangePassword = account.mustChangePassword,
                createdAt = account.createdAt,
                lastLoginAt = account.lastLoginAt,
            )
    }
}
