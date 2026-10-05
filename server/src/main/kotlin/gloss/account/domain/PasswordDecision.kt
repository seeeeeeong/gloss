package gloss.account.domain

sealed interface PasswordDecision {
    data class Accepted(
        val password: Password,
    ) : PasswordDecision

    data object TooShort : PasswordDecision

    data object TooLong : PasswordDecision

    data object Blank : PasswordDecision
}
