package gloss.domain.book.domain

/** Contents as the reader gave them, kept as given. Only [of] makes one, so every one is valid. */
@JvmInline
value class TocText private constructor(
    val value: String,
) {
    companion object {
        // Same as toc.raw's check: a long book's contents with room to spare.
        const val MAX_LENGTH = 100_000

        /** Null when there is nothing but space, too much text, or a NUL, which PostgreSQL text refuses. */
        fun of(raw: String): TocText? =
            raw
                .takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= MAX_LENGTH && '\u0000' !in it }
                ?.let(::TocText)
    }
}
