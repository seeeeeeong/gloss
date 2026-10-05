package gloss.account.domain

/** What an operator decides about a new account; the rest is set when it is made. */
data class AccountDraft(
    val loginId: LoginId,
    val displayName: DisplayName,
    val role: AccountRole,
)
