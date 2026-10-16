package gloss.domain.apikey.dto.response

import gloss.domain.apikey.domain.ApiKey
import java.time.Instant

/** Write-only: the key never comes back, only its last four and how it stands. */
data class ApiKeyResponse(
    val provider: String,
    val last4: String,
    val status: String,
    val validatedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(apiKey: ApiKey) =
            ApiKeyResponse(
                provider = apiKey.provider.code,
                last4 = apiKey.last4,
                status = apiKey.status.code,
                validatedAt = apiKey.validatedAt,
                createdAt = apiKey.createdAt,
            )
    }
}
