package gloss.llm.domain

import gloss.llm.domain.chat.FinishReason

/** [usage] is null when the stream ended without reporting it. */
data class ChatResult(
    val text: String,
    val finishReason: FinishReason,
    val usage: TokenUsage?,
)
