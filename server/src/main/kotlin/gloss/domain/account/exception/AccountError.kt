package gloss.domain.account.exception

import gloss.common.error.ErrorCode
import gloss.domain.account.policy.PasswordDecision
import org.springframework.http.HttpStatus

enum class AccountError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "account.login_failed", "아이디 또는 비밀번호가 맞지 않습니다"),
    TOO_MANY_ATTEMPTS(
        HttpStatus.TOO_MANY_REQUESTS,
        "account.too_many_attempts",
        "로그인 시도가 너무 많습니다. 15분 뒤에 다시 시도해 주세요",
    ),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "account.wrong_password", "현재 비밀번호가 맞지 않습니다"),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "account.password_too_short", "비밀번호는 10자 이상이어야 합니다"),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "account.password_too_long", "비밀번호는 72바이트를 넘을 수 없습니다"),
    PASSWORD_BLANK(HttpStatus.BAD_REQUEST, "account.password_blank", "공백만으로는 비밀번호를 만들 수 없습니다"),
    PASSWORD_COMMON(HttpStatus.BAD_REQUEST, "account.password_common", "너무 흔한 비밀번호입니다. 다른 비밀번호를 입력해 주세요"),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "account.password_unchanged", "지금과 다른 비밀번호를 입력해 주세요"),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "account.login_id_taken", "이미 쓰고 있는 아이디입니다"),
    INVALID_LOGIN_ID(
        HttpStatus.BAD_REQUEST,
        "account.invalid_login_id",
        "아이디는 3~32자의 영문 소문자, 숫자, 점, 밑줄, 하이픈입니다",
    ),
    INVALID_DISPLAY_NAME(HttpStatus.BAD_REQUEST, "account.invalid_display_name", "이름은 1~50자로 입력해 주세요"),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "account.invalid_role", "역할은 operator 또는 member입니다"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "account.not_found", "계정을 찾을 수 없습니다"),
    LAST_OPERATOR(HttpStatus.CONFLICT, "account.last_operator", "마지막 운영자는 정지할 수 없습니다"),
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
