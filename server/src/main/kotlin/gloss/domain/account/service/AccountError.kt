package gloss.domain.account.service

import gloss.common.error.ErrorCode
import gloss.domain.account.domain.PasswordDecision
import org.springframework.http.HttpStatus

enum class AccountError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "account.login_failed", "아이디나 비밀번호가 맞지 않아요."),
    TOO_MANY_ATTEMPTS(
        HttpStatus.TOO_MANY_REQUESTS,
        "account.too_many_attempts",
        "잠시 후 다시 시도해 주세요.",
    ),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "account.wrong_password", "현재 비밀번호가 맞지 않아요."),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "account.password_too_short", "10자 이상으로 정해 주세요."),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "account.password_too_long", "비밀번호는 72바이트를 넘을 수 없어요."),
    PASSWORD_BLANK(HttpStatus.BAD_REQUEST, "account.password_blank", "공백만으로는 비밀번호를 만들 수 없어요."),
    PASSWORD_COMMON(HttpStatus.BAD_REQUEST, "account.password_common", "너무 흔한 비밀번호예요. 다른 비밀번호로 정해 주세요."),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "account.password_unchanged", "지금과 다른 비밀번호로 정해 주세요."),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "account.login_id_taken", "이미 쓰고 있는 아이디예요."),
    INVALID_LOGIN_ID(
        HttpStatus.BAD_REQUEST,
        "account.invalid_login_id",
        "아이디는 3~32자의 영문 소문자, 숫자, 점, 밑줄, 하이픈으로 정해 주세요.",
    ),
    INVALID_DISPLAY_NAME(HttpStatus.BAD_REQUEST, "account.invalid_display_name", "이름은 1~50자로 정해 주세요."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "account.invalid_role", "역할은 독자나 운영자로 정해 주세요."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "account.not_found", "계정을 찾을 수 없어요."),
    LAST_OPERATOR(HttpStatus.CONFLICT, "account.last_operator", "마지막 운영자는 사용 중지할 수 없어요."),
    PASSWORD_CHANGE_REQUIRED(
        HttpStatus.FORBIDDEN,
        "account.password_change_required",
        "임시 비밀번호를 먼저 바꿔 주세요.",
    ),
    ;

    companion object {
        fun of(decision: PasswordDecision): AccountError? =
            when (decision) {
                is PasswordDecision.Accepted -> null
                PasswordDecision.TooShort -> PASSWORD_TOO_SHORT
                PasswordDecision.TooLong -> PASSWORD_TOO_LONG
                PasswordDecision.Blank -> PASSWORD_BLANK
            }
    }
}
