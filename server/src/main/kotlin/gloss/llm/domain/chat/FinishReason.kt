package gloss.llm.domain.chat

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
