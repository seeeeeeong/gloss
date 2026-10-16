package gloss.domain.setup.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class SetupError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "setup.not_found", "대화를 찾을 수 없어요."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "setup.message_not_found", "대화에 없는 검색이에요."),
    INVALID_TEXT(HttpStatus.BAD_REQUEST, "setup.invalid_text", "메시지는 1~500자로 입력해 주세요."),
    BOOK_NOT_OFFERED(HttpStatus.BAD_REQUEST, "setup.book_not_offered", "이 대화에서 찾은 책이 아니에요."),
    SESSION_CHANGED(HttpStatus.CONFLICT, "setup.session_changed", "다른 곳에서 대화가 바뀌어 새로 불러왔어요. 다시 해 주세요."),
    CONTENTS_GONE(HttpStatus.CONFLICT, "setup.contents_gone", "지금은 이 판본의 목차를 가져올 수 없어요."),
    STILL_SEARCHING(HttpStatus.CONFLICT, "setup.still_searching", "앞선 메시지에 아직 답하고 있어요. 잠시 후 다시 보내 주세요."),
    NO_BOOK_YET(HttpStatus.CONFLICT, "setup.no_book_yet", "먼저 책을 골라 주세요."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "setup.already_confirmed", "이미 이 목차로 시작했어요."),
    TURNS_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "setup.turns_busy", "지금은 요청이 많아요. 잠시 후 다시 보내 주세요."),
    TURN_TIMED_OUT(HttpStatus.GATEWAY_TIMEOUT, "setup.turn_timed_out", "답이 너무 오래 걸려 멈췄어요. 다시 보내 주세요."),
    NO_ANSWER(HttpStatus.BAD_GATEWAY, "setup.no_answer", "답을 끝내지 못했어요. 다시 보내 주세요."),
    TURN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "setup.turn_failed", "답을 만들지 못했어요. 다시 보내 주세요."),
}
