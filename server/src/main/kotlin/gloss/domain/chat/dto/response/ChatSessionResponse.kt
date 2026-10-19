package gloss.domain.chat.dto.response

import gloss.domain.chat.domain.ChatSessionSummary
import java.time.Instant
import java.util.UUID

/**
 * A conversation as the reader's list shows it: its first question and the item it started on, null
 * from the cover. [itemKeys] are the items any of its turns came from.
 */
data class ChatSessionResponse(
    val id: UUID,
    val firstQuestion: String,
    val itemKey: String?,
    val itemKeys: List<String>,
    val startedAt: Instant,
    val lastActivityAt: Instant,
) {
    companion object {
        fun from(summary: ChatSessionSummary) =
            ChatSessionResponse(
                id = summary.id,
                firstQuestion = summary.firstQuestion,
                itemKey = summary.itemKey,
                itemKeys = summary.itemKeys,
                startedAt = summary.startedAt,
                lastActivityAt = summary.lastActivityAt,
            )
    }
}
