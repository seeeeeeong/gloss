package gloss.apikey.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class ApiKeyStatus(
    val code: String,
) {
    VALID("valid"),
    INVALID("invalid"),
    UNREADABLE("unreadable"),
    REVOKED("revoked"),
    ;

    @Converter
    class Column : AttributeConverter<ApiKeyStatus, String> {
        override fun convertToDatabaseColumn(status: ApiKeyStatus): String = status.code

        override fun convertToEntityAttribute(code: String): ApiKeyStatus = entries.first { it.code == code }
    }
}
