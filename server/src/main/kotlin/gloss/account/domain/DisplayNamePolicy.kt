package gloss.account.domain

object DisplayNamePolicy {
    // Same as account.display_name's check: it fits the top bar and the operator's list.
    private const val MAX_LENGTH = 50

    /**
     * Trimmed, or null when nothing is left, too much is, or a control character is in it:
     * Kotlin's trim takes U+00A0 too, and PostgreSQL text refuses NUL.
     */
    fun normalize(raw: String): String? =
        raw.trim().takeIf { name ->
            name.codePointCount(0, name.length) in 1..MAX_LENGTH &&
                name.none { it.isISOControl() }
        }
}
