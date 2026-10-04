package gloss.account.domain

sealed interface PasswordDecision {
    data object Accepted : PasswordDecision

    data object TooShort : PasswordDecision

    data object TooLong : PasswordDecision

    data object Blank : PasswordDecision
}
