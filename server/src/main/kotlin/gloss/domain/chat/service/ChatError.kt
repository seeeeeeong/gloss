package gloss.domain.chat.service

import gloss.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ChatError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    BOOK_NOT_READY(HttpStatus.NOT_FOUND, "chat.book_not_ready", "목차를 확정한 책에서만 물을 수 있어요."),
    NO_PLAN(HttpStatus.CONFLICT, "chat.no_plan", "책의 학습 계획을 만드는 중이에요. 잠시 뒤 다시 물어 주세요."),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "chat.section_not_found", "확정된 목차에 없는 항목이에요."),
    EMPTY_ANSWER(HttpStatus.BAD_GATEWAY, "chat.empty_answer", "답이 비어 있었어요. 다시 물어 주세요."),
    ANSWERING(HttpStatus.CONFLICT, "chat.answering", "앞의 질문에 답하는 중이에요. 답이 끝난 뒤 다시 물어 주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "chat.session_not_found", "대화를 찾을 수 없어요."),
    STILL_ANSWERING(HttpStatus.CONFLICT, "chat.still_answering", "답하는 중인 대화예요. 답이 끝난 뒤 삭제해 주세요."),
}
