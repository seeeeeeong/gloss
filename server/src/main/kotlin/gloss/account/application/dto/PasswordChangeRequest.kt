package gloss.account.application.dto

import jakarta.validation.constraints.NotBlank

data class PasswordChangeRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank val newPassword: String,
)
