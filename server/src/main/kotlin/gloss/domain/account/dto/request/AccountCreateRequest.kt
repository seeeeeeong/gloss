package gloss.domain.account.dto.request

import jakarta.validation.constraints.NotBlank

data class AccountCreateRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val displayName: String,
    @field:NotBlank val role: String,
)
