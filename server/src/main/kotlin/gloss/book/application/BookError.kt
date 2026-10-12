package gloss.book.application

import gloss.book.domain.TocParser
import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class BookError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_TITLE(HttpStatus.BAD_REQUEST, "book.invalid_title", "책 제목은 1~300자로 입력해 주세요"),
    INVALID_AUTHORS(HttpStatus.BAD_REQUEST, "book.invalid_authors", "저자는 20명까지, 이름은 100자까지 입력해 주세요"),
    INVALID_PUBLISHER(HttpStatus.BAD_REQUEST, "book.invalid_publisher", "출판사는 100자까지 입력해 주세요"),
    INVALID_ISBN(HttpStatus.BAD_REQUEST, "book.invalid_isbn", "ISBN-13이 올바르지 않습니다. 978 또는 979로 시작하는 13자리입니다"),
    ISBN_TAKEN(HttpStatus.CONFLICT, "book.isbn_taken", "이미 책장에 있는 책입니다"),
    INVALID_TOC(HttpStatus.BAD_REQUEST, "book.invalid_toc", "목차는 10만 자까지 입력해 주세요"),
    TOC_EMPTY(HttpStatus.BAD_REQUEST, "book.toc_empty", "목차를 한 줄 이상 입력해 주세요"),
    TOC_TOO_MANY_ITEMS(HttpStatus.BAD_REQUEST, "book.toc_too_many_items", "목차는 2,000줄까지 입력할 수 있습니다"),
    TOC_LINE_TOO_LONG(HttpStatus.BAD_REQUEST, "book.toc_line_too_long", "목차 한 줄은 500자까지입니다"),
    TOC_TOO_DEEP(HttpStatus.BAD_REQUEST, "book.toc_too_deep", "목차는 6단계까지 입력할 수 있습니다"),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "book.not_found", "책을 찾을 수 없습니다"),
    TOC_NOT_FOUND(HttpStatus.NOT_FOUND, "book.toc_not_found", "목차를 찾을 수 없습니다"),
    TOC_NOT_CONFIRMED(HttpStatus.NOT_FOUND, "book.toc_not_confirmed", "아직 확정한 목차가 없습니다"),
    TOC_OUTDATED(HttpStatus.CONFLICT, "book.toc_outdated", "더 새로운 목차가 있습니다. 새로 고친 뒤 다시 확정해 주세요"),
    ;

    companion object {
        fun of(result: TocParser.Result): BookError? =
            when (result) {
                is TocParser.Result.Parsed -> null
                TocParser.Result.Empty -> TOC_EMPTY
                TocParser.Result.TooManyItems -> TOC_TOO_MANY_ITEMS
                TocParser.Result.LineTooLong -> TOC_LINE_TOO_LONG
                TocParser.Result.TooDeep -> TOC_TOO_DEEP
            }
    }
}
