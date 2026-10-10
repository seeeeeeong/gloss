package gloss.llm.domain.chat

data class ChatMessage(
    val role: Role,
    val content: String,
)

enum class Role(
    val code: String,
) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}

/** DeepSeek thinks by default, which costs output tokens; every call says which it wants. */
enum class Thinking(
    val code: String,
) {
    DISABLED("disabled"),
    ENABLED("enabled"),
}

/** DeepSeek's finish_reason values; only STOP is a complete answer worth storing. */
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
