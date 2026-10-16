package gloss.domain.llm.service

import gloss.common.error.ErrorCode
import gloss.domain.llm.domain.LlmCallFailed
import org.springframework.http.HttpStatus

enum class LlmError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PROMPT_TOO_LONG(HttpStatus.BAD_REQUEST, "llm.prompt_too_long", "보낼 내용이 너무 길어요. 줄여서 다시 시도해 주세요."),
    NO_KEY(HttpStatus.CONFLICT, "llm.no_key", "마이페이지에서 DeepSeek API 키를 먼저 등록해 주세요."),
    KEY_UNREADABLE(HttpStatus.CONFLICT, "llm.key_unreadable", "저장된 API 키를 읽지 못했어요. 마이페이지에서 키를 다시 입력해 주세요."),
    KEY_REJECTED(HttpStatus.CONFLICT, "llm.key_rejected", "DeepSeek이 API 키를 받지 않았어요. 마이페이지에서 키를 확인해 주세요."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "llm.insufficient_balance", "DeepSeek 계정의 잔액이 부족해요. DeepSeek에서 충전해 주세요."),
    REQUEST_REFUSED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "llm.request_refused",
        "DeepSeek이 요청을 받지 않았어요. 다시 해도 같으면 운영자에게 알려 주세요.",
    ),
    PROVIDER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "llm.provider_busy", "DeepSeek이 바빠요. 잠시 후 다시 시도해 주세요."),
    TOOLS_REFUSED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "llm.tools_refused",
        "모델에 보낼 도구 설정이 잘못됐어요. 운영자에게 알려 주세요.",
    ),
    PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "llm.provider_failed", "DeepSeek에서 답을 받지 못했어요. 잠시 후 다시 시도해 주세요."),
    ;

    companion object {
        fun of(kind: LlmCallFailed.Kind): LlmError =
            when (kind) {
                LlmCallFailed.Kind.REJECTED_KEY -> KEY_REJECTED

                LlmCallFailed.Kind.INSUFFICIENT_BALANCE -> INSUFFICIENT_BALANCE

                LlmCallFailed.Kind.RATE_LIMITED -> PROVIDER_BUSY

                LlmCallFailed.Kind.REFUSED_REQUEST -> REQUEST_REFUSED

                LlmCallFailed.Kind.PROVIDER_ERROR,
                LlmCallFailed.Kind.NETWORK,
                LlmCallFailed.Kind.CANCELLED,
                LlmCallFailed.Kind.CALLER_FAILED,
                -> PROVIDER_FAILED
            }
    }
}
