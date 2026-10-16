package gloss.domain.apikey.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ApiKeyError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_KEY(HttpStatus.BAD_REQUEST, "apikey.invalid_key", "API 키 형식이 맞지 않아요."),
    KEY_REJECTED(HttpStatus.BAD_REQUEST, "apikey.key_rejected", "DeepSeek이 이 키를 받지 않았어요. 키를 다시 확인해 주세요."),
    KEY_CHECK_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "apikey.key_check_unavailable",
        "DeepSeek에 연결하지 못해 키를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
    ),
    NOT_REGISTERED(HttpStatus.NOT_FOUND, "apikey.not_registered", "등록된 API 키가 없어요."),
}
