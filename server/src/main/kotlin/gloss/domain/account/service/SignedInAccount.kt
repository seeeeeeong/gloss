package gloss.domain.account.service

import gloss.domain.account.domain.AccountRole
import gloss.domain.account.dto.response.MyAccountResponse

/** What sign-in returns: the response, and what the session needs to remember. */
data class SignedInAccount(
    val account: MyAccountResponse,
    val role: AccountRole,
    val sessionEpoch: Int,
)
