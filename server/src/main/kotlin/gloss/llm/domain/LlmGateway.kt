package gloss.llm.domain

import gloss.llm.domain.chat.ChatMessage
import gloss.llm.domain.chat.FinishReason
import gloss.llm.domain.chat.Thinking

/**
 * A streaming chat completion on the reader's own key. [onText] gets each piece as it comes and
 * must return promptly, handing it off rather than waiting on a slow client. [isCancelled] is
 * asked at every line of the stream and every second from another thread: from any thread, but
 * never twice at once. True closes the connection.
 */
interface LlmGateway {
    fun stream(
        secret: String,
        request: ChatRequest,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult
}

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val thinking: Thinking,
)

/** [usage] is null when the stream ended without reporting it. */
data class ChatResult(
    val text: String,
    val finishReason: FinishReason,
    val usage: TokenUsage?,
)

/** As the provider reported it: hit and miss both, never one derived from the other. */
data class TokenUsage(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val reasoningTokens: Long?,
)
