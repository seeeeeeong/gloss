package gloss.domain.account.event

enum class SignInFailure {
    UNKNOWN_LOGIN_ID,
    WRONG_PASSWORD,
    DISABLED,
}
