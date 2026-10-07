package gloss.llm.domain.chat

/** DeepSeek thinks by default, which costs output tokens; every call says which it wants. */
enum class Thinking(
    val code: String,
) {
    DISABLED("disabled"),
    ENABLED("enabled"),
}
