package gloss.apikey.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class KeyProvider(
    val code: String,
) {
    DEEPSEEK("deepseek"),
    ;

    @Converter
    class Column : AttributeConverter<KeyProvider, String> {
        override fun convertToDatabaseColumn(provider: KeyProvider): String = provider.code

        override fun convertToEntityAttribute(code: String): KeyProvider = entries.first { it.code == code }
    }
}
