package gloss.domain.account.domain

/** A name shown for an account, trimmed. Only [of] makes one, so every one is valid. */
@JvmInline
value class DisplayName private constructor(
    val value: String,
) {
    companion object {
        // Same as account.display_name's check: it fits the top bar and the operator's list.
        private const val MAX_LENGTH = 50

        /**
         * Null when nothing is left after trimming, too much is, or a control character is in it:
         * Kotlin's trim takes U+00A0 too, and PostgreSQL text refuses NUL.
         */
        fun of(raw: String): DisplayName? =
            raw
                .trim()
                .takeIf { name ->
                    name.codePointCount(0, name.length) in 1..MAX_LENGTH &&
                        name.none { it.isISOControl() }
                }?.let(::DisplayName)
    }
}
