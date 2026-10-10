package gloss.account.application.dto

import gloss.account.domain.Account
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class AccountCreateRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val role: String,
)

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

/**
 * The temporary password appears here once, under Cache-Control: no-store, and is stored
 * nowhere but as a hash. The operator hands it to the reader.
 */
data class CreatedAccountResponse(
    val account: AdminAccountResponse,
    val temporaryPassword: String,
)

/** Shown once to the operator, under Cache-Control: no-store; stored only as a hash. */
data class TemporaryPasswordResponse(
    val temporaryPassword: String,
)
