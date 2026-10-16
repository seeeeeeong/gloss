package gloss.domain.setup.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class SetupError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "setup.not_found", "대화를 찾을 수 없습니다"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "setup.message_not_found", "대화에 없는 검색입니다"),
    INVALID_TEXT(HttpStatus.BAD_REQUEST, "setup.invalid_text", "메시지는 1~100자로 입력해 주세요"),
    BOOK_NOT_OFFERED(HttpStatus.BAD_REQUEST, "setup.book_not_offered", "이 대화에서 찾은 책이 아닙니다"),
    SESSION_CHANGED(HttpStatus.CONFLICT, "setup.session_changed", "다른 곳에서 대화가 바뀌어 새로 불러왔습니다. 다시 해 주세요"),
    CONTENTS_GONE(HttpStatus.CONFLICT, "setup.contents_gone", "지금은 이 판본의 목차를 가져올 수 없습니다"),
    STILL_SEARCHING(HttpStatus.CONFLICT, "setup.still_searching", "앞선 메시지의 책을 아직 찾고 있습니다. 잠시 후 다시 보내 주세요"),
    NO_BOOK_YET(HttpStatus.CONFLICT, "setup.no_book_yet", "먼저 책을 골라 주세요"),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "setup.already_confirmed", "이미 이 목차로 시작했습니다"),
}
