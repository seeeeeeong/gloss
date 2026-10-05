package gloss.account.domain

/** A password a reader may choose. Only [check] makes one, inside [PasswordDecision.Accepted]. */
@JvmInline
value class Password private constructor(
    val value: String,
) {
    companion object {
        // NIST SP 800-63B-4 asks 15 for a password alone; 10 with the breach list and sign-in throttling.
        private const val MIN_LENGTH = 10

        // bcrypt reads 72 bytes and silently ignores the rest.
        private const val MAX_BYTES = 72

        fun check(raw: String): PasswordDecision =
            when {
                // Sign-in refuses a blank password, so an account given one could never sign in.
                raw.isBlank() -> PasswordDecision.Blank

                raw.codePointCount(0, raw.length) < MIN_LENGTH -> PasswordDecision.TooShort

                raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES -> PasswordDecision.TooLong

                else -> PasswordDecision.Accepted(Password(raw))
            }
    }
}
