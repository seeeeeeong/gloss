package gloss.domain.chat.dto.response

import gloss.domain.chat.domain.ChatLine
import gloss.domain.chat.domain.ChatStatus
import java.time.Instant
import java.util.UUID

/**
 * A message as the reader sees it, naming its conversation; a reader's names the turn the page sent
 * it as, and a reply the message it answers ([replyTo]). A reply has a [status]; [code] says why one
 * did not end done, a finish reason when cut, an error code when failed, and [message] is what a
 * failure tells the reader.
 */
data class ChatMessageResponse(
    val sessionId: UUID,
    val seq: Int,
    val role: String,
    val turnId: UUID?,
    val replyTo: Int?,
    val itemKey: String?,
    val quote: String?,
    val text: String,
    val status: String?,
    val code: String?,
    val message: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            line: ChatLine,
            sessionId: UUID,
            message: String? = null,
        ): ChatMessageResponse {
            val answered = line.answered
            val code =
                when (answered?.status) {
                    ChatStatus.FAILED -> answered.errorCode
                    ChatStatus.CUT -> answered.finishReason
                    else -> null
                }
            return ChatMessageResponse(
                sessionId = sessionId,
                seq = line.seq,
                role = line.role.code,
                turnId = line.asked?.turnId,
                replyTo = answered?.replyTo,
                itemKey = line.itemKey,
                quote = line.asked?.quote,
                text = line.text,
                status = answered?.status?.code,
                code = code,
                message = message,
                createdAt = line.createdAt,
            )
        }
    }
}
