package gloss.account.application.dto

import jakarta.validation.constraints.NotBlank

data class ProfileUpdateRequest(
    @field:NotBlank val displayName: String,
)
