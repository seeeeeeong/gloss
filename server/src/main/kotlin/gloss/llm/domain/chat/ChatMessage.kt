package gloss.llm.domain.chat

data class ChatMessage(
    val role: Role,
    val content: String,
)
