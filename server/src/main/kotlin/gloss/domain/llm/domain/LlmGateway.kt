package gloss.domain.llm.domain

import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.domain.chat.ToolDefinition

/**
 * A streaming chat completion on the reader's own key. [onText] gets each piece as it comes and
 * must return promptly, handing it off rather than waiting on a slow client. [isCancelled] is
 * asked at every line of the stream and every second from another thread: from any thread, but
 * never twice at once. True closes the connection. An answer the provider has finished, with its
 * usage, is returned even when a cancel or a failed check comes as it finishes: it is billed, and
 * the caller still knows it asked.
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
    val format: ReplyFormat = ReplyFormat.TEXT,
    val tools: List<ToolDefinition> = emptyList(),
    val toolChoice: ToolChoice = ToolChoice.Auto,
)

/**
 * [usage] is null when the stream ended without reporting it. [toolCalls] are whole, in the order
 * the model made them; [reasoning] is the thinking, which a later call offering tools sends back.
 */
data class ChatResult(
    val text: String,
    val finishReason: FinishReason,
    val usage: TokenUsage?,
    val toolCalls: List<ToolCall> = emptyList(),
    val reasoning: String = "",
)

/** As the provider reported it: hit and miss both, never one derived from the other. */
data class TokenUsage(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val reasoningTokens: Long?,
)
