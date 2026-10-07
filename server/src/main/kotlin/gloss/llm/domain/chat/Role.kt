package gloss.llm.domain.chat

enum class Role(
    val code: String,
) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}
