package gloss.domain.llm.domain.chat

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ChatMessageTest {
    private val called = ToolCall(id = "call_a", name = "search_web", arguments = "{}")

    @Test
    fun `a tool result answers the call it names`() {
        assertThat(ChatMessage.toolResult(toolCallId = "call_a", content = "results"))
            .isEqualTo(ChatMessage(Role.TOOL, "results", toolCallId = "call_a"))
    }

    @Test
    fun `a tool turn must name its call, and no other turn may`() {
        assertThatThrownBy { ChatMessage(Role.TOOL, "results") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            ChatMessage(Role.USER, "hi", toolCallId = "call_a")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `only an assistant turn carries tool calls or reasoning`() {
        assertThatThrownBy {
            ChatMessage(Role.USER, "hi", toolCalls = listOf(called))
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            ChatMessage(Role.SYSTEM, "rules", reasoning = "hmm")
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(
            ChatMessage(role = Role.ASSISTANT, content = "", toolCalls = listOf(called), reasoning = "hmm").toolCalls,
        ).containsExactly(called)
    }

    @Test
    fun `finish reasons are read from the provider's words, tool_calls included`() {
        assertThat(FinishReason.of("tool_calls")).isEqualTo(FinishReason.TOOL_CALLS)
        assertThat(FinishReason.of("something_new")).isEqualTo(FinishReason.UNKNOWN)
    }
}
