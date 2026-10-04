package gloss.account

import gloss.common.ErrorCode
import org.springframework.http.HttpStatus

enum class AccountError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "account.login_failed", "아이디 또는 비밀번호가 맞지 않습니다"),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "account.wrong_password", "현재 비밀번호가 맞지 않습니다"),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "account.password_too_short", "비밀번호는 15자 이상이어야 합니다"),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "account.password_too_long", "비밀번호는 72바이트를 넘을 수 없습니다"),
    PASSWORD_BLANK(HttpStatus.BAD_REQUEST, "account.password_blank", "공백만으로는 비밀번호를 만들 수 없습니다"),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "account.password_unchanged", "지금과 다른 비밀번호를 입력해 주세요"),
    PASSWORD_CHANGE_REQUIRED(
        HttpStatus.FORBIDDEN,
        "account.password_change_required",
        "임시 비밀번호를 먼저 바꿔 주세요",
    ),
    ;

    companion object {
        fun of(decision: PasswordDecision): AccountError? =
            when (decision) {
                PasswordDecision.Accepted -> null
                PasswordDecision.TooShort -> PASSWORD_TOO_SHORT
                PasswordDecision.TooLong -> PASSWORD_TOO_LONG
                PasswordDecision.Blank -> PASSWORD_BLANK
            }
    }
}
