package gloss.apikey.application.dto

import jakarta.validation.constraints.NotBlank

data class ApiKeySaveRequest(
    // Its length is ApiKeySecret's to judge, so a bad key gets one answer however long it is.
    @field:NotBlank
    val key: String,
) {
    // A data class prints its fields, and a request ends up in logs and error reports.
    override fun toString(): String = "ApiKeySaveRequest(key=…)"
}
