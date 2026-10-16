package gloss.domain.setup.domain

/** What a reader says in a setup session, on one line. Only [of] makes one, so every one is valid. */
@JvmInline
value class ReaderText private constructor(
    val value: String,
) {
    companion object {
        // Every message is a book search until a model reads them, and the source takes 100.
        private const val MAX_LENGTH = 100
        private val SPACES = Regex("\\s+")

        /**
         * Line breaks and runs of spaces become one space; null when nothing is left, too much is,
         * or a control character is.
         */
        fun of(raw: String): ReaderText? =
            raw
                .replace(SPACES, " ")
                .trim()
                .takeIf { text ->
                    text.codePointCount(0, text.length) in 1..MAX_LENGTH &&
                        text.none { it.isISOControl() }
                }?.let(::ReaderText)
    }
}
