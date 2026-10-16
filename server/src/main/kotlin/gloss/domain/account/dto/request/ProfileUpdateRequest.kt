package gloss.domain.account.dto.request

import jakarta.validation.constraints.NotBlank

data class ProfileUpdateRequest(
    @field:NotBlank val displayName: String,
)
