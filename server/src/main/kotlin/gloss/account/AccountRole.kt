package gloss.account

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class AccountRole(
    val code: String,
) {
    OPERATOR("operator"),
    MEMBER("member"),
    ;

    @Converter
    class Column : AttributeConverter<AccountRole, String> {
        override fun convertToDatabaseColumn(role: AccountRole): String = role.code

        override fun convertToEntityAttribute(code: String): AccountRole = entries.first { it.code == code }
    }
}
