package gloss.account.domain

object PasswordPolicy {
    // NIST SP 800-63B-4 asks 15 for a password alone; 10 with the breach list and sign-in throttling.
    private const val MIN_LENGTH = 10

    // bcrypt reads 72 bytes and silently ignores the rest.
    private const val MAX_BYTES = 72

    fun check(password: String): PasswordDecision =
        when {
            // Sign-in refuses a blank password, so an account given one could never sign in.
            password.isBlank() -> PasswordDecision.Blank

            password.codePointCount(0, password.length) < MIN_LENGTH -> PasswordDecision.TooShort

            password.toByteArray(Charsets.UTF_8).size > MAX_BYTES -> PasswordDecision.TooLong

            else -> PasswordDecision.Accepted
        }
}
