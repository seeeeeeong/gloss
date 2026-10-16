package gloss.domain.book.domain

/** A book's title, trimmed. Only [of] makes one, so every one is valid. */
@JvmInline
value class BookTitle private constructor(
    val value: String,
) {
    companion object {
        // A long title with its subtitle and series fits, as Korean technical books carry them;
        // book.title's check is the same.
        private const val MAX_LENGTH = 300

        /** Null when nothing is left after trimming, too much is, or a control character is in it. */
        fun of(raw: String): BookTitle? =
            raw
                .trim()
                .takeIf { title ->
                    title.codePointCount(0, title.length) in 1..MAX_LENGTH && title.none { it.isISOControl() }
                }?.let(::BookTitle)
    }
}
