package gloss.domain.llm.infrastructure

import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.domain.chat.ToolDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The request as it reaches the provider, written through the gateway as a call writes it. */
class DeepSeekRequestWriterTest : DeepSeekFixture() {
    @Test
    fun `a call that offers no tools is sent byte for byte as before tools existed`() {
        answering { it.streams(chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
            .stream(secret = "sk-reader", request = request, onText = {}, isCancelled = { false })

        assertThat(body).isEqualTo(
            """{"model":"deepseek-flash","messages":[{"role":"system","content":"rules"},""" +
                """{"role":"user","content":"question"}],"max_tokens":512,"thinking":{"type":"disabled"},""" +
                """"stream":true,"stream_options":{"include_usage":true}}""",
        )
    }

    @Test
    fun `a call that offers tools sends them, its choice, and the turns that called and answered them`() {
        val schema = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}"""
        val called = ToolCall(id = "call_a", name = "search_web", arguments = """{"query": "os"}""")
        val withTools =
            request.copy(
                messages =
                    request.messages +
                        ChatMessage(
                            role = Role.ASSISTANT,
                            content = "",
                            toolCalls = listOf(called),
                            reasoning = "look it up",
                        ) +
                        ChatMessage.toolResult(toolCallId = "call_a", content = "results"),
                tools =
                    listOf(
                        ToolDefinition(name = "search_web", description = "Search the web", parameters = schema),
                    ),
                toolChoice = ToolChoice.Named("search_web"),
            )

        answering { it.streams(chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
            .stream(secret = "sk-reader", request = withTools, onText = {}, isCancelled = { false })

        val sent = json.readTree(body)
        assertThat(sent.get("tools")).isEqualTo(
            json.readTree(
                """[{"type":"function","function":{"name":"search_web","description":"Search the web",""" +
                    """"parameters":$schema}}]""",
            ),
        )
        assertThat(
            sent.get("tool_choice"),
        ).isEqualTo(json.readTree("""{"type":"function","function":{"name":"search_web"}}"""))
        assertThat(sent.get("messages").get(2)).isEqualTo(
            json.readTree(
                """{"role":"assistant","content":"","tool_calls":[{"id":"call_a","type":"function",""" +
                    """"function":{"name":"search_web","arguments":"{\"query\": \"os\"}"}}],""" +
                    """"reasoning_content":"look it up"}""",
            ),
        )
        assertThat(sent.get("messages").get(3))
            .isEqualTo(json.readTree("""{"role":"tool","content":"results","tool_call_id":"call_a"}"""))
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(512)
        assertThat(sent.get("thinking").get("type").asString()).isEqualTo("disabled")
    }

    @Test
    fun `a choice of auto, none or required is sent as the provider's word for it`() {
        fun sentChoice(choice: ToolChoice): String {
            val tool = ToolDefinition(name = "search_web", description = "Search the web", parameters = "{}")
            answering { it.streams(chunk(finish = "stop"), usage, "data: [DONE]\n\n") }
                .stream(
                    secret = "sk-reader",
                    request = request.copy(tools = listOf(tool), toolChoice = choice),
                    onText = {},
                    isCancelled = { false },
                )
            stop()
            return json.readTree(body).get("tool_choice").asString()
        }

        assertThat(sentChoice(ToolChoice.Auto)).isEqualTo("auto")
        assertThat(sentChoice(ToolChoice.None)).isEqualTo("none")
        assertThat(sentChoice(ToolChoice.Required)).isEqualTo("required")
    }
}
