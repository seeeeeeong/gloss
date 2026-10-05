package gloss.domain.account.dto

import gloss.domain.account.entity.AccountRole

/** What sign-in returns: the response, and what the session needs to remember. */
data class SignedInAccount(
    val account: MyAccountResponse,
    val role: AccountRole,
    val sessionEpoch: Int,
)
