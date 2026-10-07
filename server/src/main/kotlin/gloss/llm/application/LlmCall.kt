package gloss.llm.application

import gloss.budget.application.Purpose
import gloss.llm.domain.chat.ChatMessage
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
