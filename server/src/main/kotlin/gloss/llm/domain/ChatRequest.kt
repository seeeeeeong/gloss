package gloss.llm.domain

import gloss.llm.domain.chat.ChatMessage
import gloss.llm.domain.chat.Thinking

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val thinking: Thinking,
)
