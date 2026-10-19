package gloss.domain.chat.dto.response

import java.util.UUID

/**
 * One of a reader's conversations about a book, in order; with no [sessionId], none has started.
 * [answering] is true while a turn is under way, from this page or another, until its reply is kept.
 */
data class ChatResponse(
    val sessionId: UUID?,
    val messages: List<ChatMessageResponse>,
    val answering: Boolean = false,
)
