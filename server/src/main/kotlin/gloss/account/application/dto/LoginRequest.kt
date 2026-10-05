package gloss.account.application.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
)
