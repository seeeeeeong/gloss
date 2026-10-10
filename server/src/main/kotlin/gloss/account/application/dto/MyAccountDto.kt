package gloss.account.application.dto

import gloss.account.domain.Account
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class MyAccountResponse(
    val id: UUID,
    val loginId: String,
    val displayName: String,
    val role: String,
    val mustChangePassword: Boolean,
) {
    companion object {
        fun from(account: Account) =
            MyAccountResponse(
                id = account.id,
                loginId = account.loginId,
                displayName = account.displayName,
                role = account.role.code,
                mustChangePassword = account.mustChangePassword,
            )
    }
}

data class ProfileUpdateRequest(
    @field:NotBlank val displayName: String,
)

data class PasswordChangeRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank val newPassword: String,
)
