package gloss.llm.application

import gloss.llm.domain.chat.FinishReason

/** Only a reply that finished with [FinishReason.STOP] is complete; the caller checks before storing it. */
data class LlmReply(
    val text: String,
    val finishReason: FinishReason,
    val cancelled: Boolean = false,
)
