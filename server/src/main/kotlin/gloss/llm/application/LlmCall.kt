package gloss.llm.application

import gloss.budget.application.Purpose
import gloss.llm.domain.chat.ChatMessage
import gloss.llm.domain.chat.FinishReason
import gloss.llm.domain.chat.Thinking
import java.util.UUID

/** [maxTokens] and [thinking] have no defaults: each caller decides, and pays for, its own. */
data class LlmCall(
    val accountId: UUID,
    val purpose: Purpose,
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val thinking: Thinking,
)

/** Only a reply that finished with [FinishReason.STOP] is complete; the caller checks before storing it. */
data class LlmReply(
    val text: String,
    val finishReason: FinishReason,
    val cancelled: Boolean = false,
)
