package gloss.account.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class AccountStatus(
    val code: String,
) {
    ACTIVE("active"),
    DISABLED("disabled"),
    ;

    @Converter
    class Column : AttributeConverter<AccountStatus, String> {
        override fun convertToDatabaseColumn(status: AccountStatus): String = status.code

        override fun convertToEntityAttribute(code: String): AccountStatus = entries.first { it.code == code }
    }
}
