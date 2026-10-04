package gloss.domain.account.dto

import jakarta.validation.constraints.NotBlank

data class ProfileUpdateRequest(
    @field:NotBlank val displayName: String,
)
