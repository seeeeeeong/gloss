package gloss.account

sealed interface PasswordChangeResult {
    data class Changed(
        val sessionEpoch: Int,
    ) : PasswordChangeResult

    data object WrongPassword : PasswordChangeResult

    data object SameAsCurrent : PasswordChangeResult

    data class Rejected(
        val decision: PasswordDecision,
    ) : PasswordChangeResult
}
