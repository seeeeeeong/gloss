package gloss.domain.post.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class PostError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post.not_found", "아직 글이 없습니다"),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "post.section_not_found", "확정된 목차에 없는 항목입니다"),
}
