package gloss.domain.chat.dto.response

/**
 * A reader's conversation about a book, in order; empty before the first question. [answering] is
 * true while a turn is under way, from this page or another, until its reply is kept.
 */
data class ChatResponse(
    val messages: List<ChatMessageResponse>,
    val answering: Boolean = false,
)
