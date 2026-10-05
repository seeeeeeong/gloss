package gloss.account.domain.event

enum class SignInFailure {
    UNKNOWN_LOGIN_ID,
    WRONG_PASSWORD,
    DISABLED,
}
