package gloss.domain.llm.service

import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.ToolCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LlmCallTest {
    private val called = ToolCall(id = "call_a", name = "search_web", arguments = """{"query": "os"}""")

    @Test
    fun `a reply that called tools becomes the assistant turn that carries them and its thinking`() {
        val reply =
            LlmReply(text = "", finishReason = FinishReason.TOOL_CALLS, toolCalls = listOf(called), reasoning = "look")

        assertThat(reply.asAssistantTurn()).isEqualTo(
            ChatMessage(role = Role.ASSISTANT, content = "", toolCalls = listOf(called), reasoning = "look"),
        )
    }

    @Test
    fun `a reply without thinking sends none back`() {
        val reply = LlmReply(text = "Hello", finishReason = FinishReason.STOP)

        assertThat(reply.asAssistantTurn()).isEqualTo(ChatMessage(Role.ASSISTANT, "Hello"))
    }
}
