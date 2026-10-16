package gloss.domain.book.domain

/** An ISBN-13 as digits only, its check digit verified. Only [of] makes one, so every one is valid. */
@JvmInline
value class Isbn13 private constructor(
    val value: String,
) {
    companion object {
        private val DIGITS = Regex("97[89][0-9]{10}")

        // Any space or dash as printed or copied, a no-break or full-width space among them.
        private val SEPARATORS = Regex("[\\s\\p{Z}\\p{Pd}]")
        private const val EVEN_WEIGHT = 3
        private const val MODULUS = 10

        /** Hyphens and spaces as printed on a book are dropped; anything else wrong is null. */
        fun of(raw: String): Isbn13? =
            raw
                .replace(SEPARATORS, "")
                .takeIf { DIGITS.matches(it) && checks(it) }
                ?.let(::Isbn13)

        // Weights alternate 1 and 3; the thirteen digits sum to a multiple of ten.
        private fun checks(digits: String): Boolean =
            digits.withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 1 else EVEN_WEIGHT } % MODULUS == 0
    }
}
