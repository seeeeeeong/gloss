package gloss.domain.account.policy

object PasswordPolicy {
    // NIST SP 800-63B-4: at least 15 characters when a password is the only factor.
    private const val MIN_LENGTH = 15

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
