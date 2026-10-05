package gloss.account.application.dto

import gloss.account.domain.AccountRole

/** What sign-in returns: the response, and what the session needs to remember. */
data class SignedInAccount(
    val account: MyAccountResponse,
    val role: AccountRole,
    val sessionEpoch: Int,
)
