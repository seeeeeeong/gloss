package gloss.common.error

import org.springframework.http.HttpStatus

enum class CommonError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "common.invalid_input", "입력값을 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "common.unauthorized", "로그인이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "common.forbidden", "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "common.not_found", "찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "common.method_not_allowed", "지원하지 않는 요청입니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "common.unsupported_media_type", "지원하지 않는 형식입니다"),
    INVALID_CSRF(HttpStatus.FORBIDDEN, "common.invalid_csrf", "페이지를 새로고침한 뒤 다시 시도해 주세요"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "common.internal", "잠시 후 다시 시도해 주세요"),
}
