package gloss.domain.account.entity

/** What an operator decides about a new account; the rest is set when it is made. */
data class AccountDraft(
    val loginId: String,
    val displayName: String,
    val role: AccountRole,
)
