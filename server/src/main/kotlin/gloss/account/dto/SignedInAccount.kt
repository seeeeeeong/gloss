package gloss.account.dto

import gloss.account.AccountRole

/** What sign-in returns: the response, and what the session needs to remember. */
data class SignedInAccount(
    val me: MeResponse,
    val role: AccountRole,
    val sessionEpoch: Int,
)
