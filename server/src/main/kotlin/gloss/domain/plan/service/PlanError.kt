package gloss.domain.plan.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class PlanError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "plan.book_not_found", "책을 찾을 수 없습니다"),
}
