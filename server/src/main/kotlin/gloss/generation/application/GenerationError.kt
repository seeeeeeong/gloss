package gloss.generation.application

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class GenerationError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "generation.account_disabled", "정지된 계정은 글을 만들 수 없습니다"),
}
