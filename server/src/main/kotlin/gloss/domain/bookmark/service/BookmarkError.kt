package gloss.domain.bookmark.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class BookmarkError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    BOOK_NOT_READY(HttpStatus.NOT_FOUND, "bookmark.book_not_ready", "목차를 확정한 책에서만 저장할 수 있어요."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "bookmark.item_not_found", "확정된 목차에 없는 항목이에요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "bookmark.not_found", "책갈피를 찾을 수 없어요."),
}
