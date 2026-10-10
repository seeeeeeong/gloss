package gloss.apikey.application.dto

import gloss.apikey.domain.ApiKey
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class ApiKeySaveRequest(
    // Its length is ApiKeySecret's to judge, so a bad key gets one answer however long it is.
    @field:NotBlank
    val key: String,
) {
    // A data class prints its fields, and a request ends up in logs and error reports.
    override fun toString(): String = "ApiKeySaveRequest(key=…)"
}

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
