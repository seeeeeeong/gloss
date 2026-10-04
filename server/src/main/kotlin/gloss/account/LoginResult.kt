package gloss.account

sealed interface LoginResult {
    data class Succeeded(
        val account: AccountSummary,
        val sessionEpoch: Int,
    ) : LoginResult

    data object Failed : LoginResult
}
