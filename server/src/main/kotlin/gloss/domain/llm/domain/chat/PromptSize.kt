package gloss.domain.llm.domain.chat

import gloss.domain.llm.domain.PromptSizePolicy

/** The most prompt tokens messages can bill, by the bound a reservation uses: for a caller sizing what it sends. */
object PromptSize {
    fun upperBound(messages: List<ChatMessage>): Long {
        val contents = messages.flatMap { listOfNotNull(it.content, it.toolCallId, it.reasoning) }
        val calls = messages.flatMap { it.toolCalls }.map { it.id + it.name + it.arguments }
        return PromptSizePolicy.upperBoundTokens(contents, thinking = false) +
            PromptSizePolicy.toolTokens(definitions = emptyList(), calls = calls)
    }
}
