package gloss.domain.llm.domain.chat

/**
 * One turn. An assistant turn may carry the [toolCalls] the model made, and a tool turn answers
 * one of them by [toolCallId]. With thinking on and tools offered, DeepSeek wants every earlier
 * assistant turn's [reasoning] sent back, or refuses the call with a 400.
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val reasoning: String? = null,
) {
    init {
        require((role == Role.TOOL) == (toolCallId != null)) { "Only a tool turn answers a call, and it must" }
        require(role == Role.ASSISTANT || (toolCalls.isEmpty() && reasoning == null)) {
            "Only an assistant turn carries tool calls or reasoning"
        }
    }

    companion object {
        fun toolResult(
            toolCallId: String,
            content: String,
        ) = ChatMessage(role = Role.TOOL, content = content, toolCallId = toolCallId)
    }
}

enum class Role(
    val code: String,
) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
}

/** DeepSeek thinks by default, which costs output tokens; every call says which it wants. */
enum class Thinking(
    val code: String,
) {
    DISABLED("disabled"),
    ENABLED("enabled"),
}

/** JSON is the provider's JSON output: the reply is one valid JSON value, and the prompt must ask for one. */
enum class ReplyFormat {
    TEXT,
    JSON,
}

/**
 * DeepSeek's finish_reason values. Only STOP is a complete answer worth storing; TOOL_CALLS is a
 * normal ending too, that hands the turn back to the caller to run the calls.
 */
enum class FinishReason(
    val code: String,
) {
    STOP("stop"),
    LENGTH("length"),
    CONTENT_FILTER("content_filter"),
    TOOL_CALLS("tool_calls"),
    INSUFFICIENT_SYSTEM_RESOURCE("insufficient_system_resource"),
    ABORTED("aborted"),

    /** A value the provider added after this was written; treated as not complete. */
    UNKNOWN("unknown"),
    ;

    companion object {
        fun of(code: String?): FinishReason = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
