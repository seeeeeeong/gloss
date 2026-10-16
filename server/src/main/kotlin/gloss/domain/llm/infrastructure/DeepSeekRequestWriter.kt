package gloss.domain.llm.infrastructure

import gloss.domain.llm.domain.ChatRequest
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import tools.jackson.databind.json.JsonMapper

/**
 * A request as DeepSeek's chat completions take it. A call without tools is written exactly as
 * before tools existed, so its prompt, its cache prefix and its cost stay the same.
 */
internal class DeepSeekRequestWriter(
    private val json: JsonMapper,
) {
    fun bytesOf(request: ChatRequest): ByteArray = json.writeValueAsBytes(body(request))

    private fun body(request: ChatRequest): Map<String, Any> =
        mapOf(
            "model" to request.model,
            "messages" to request.messages.map(::messageOf),
            "max_tokens" to request.maxTokens,
            "thinking" to mapOf("type" to request.thinking.code),
            "stream" to true,
            // Without it a stream reports no usage, and the ledger could only guess.
            "stream_options" to mapOf("include_usage" to true),
        ) + (if (request.format == ReplyFormat.JSON) mapOf("response_format" to JSON_OBJECT) else emptyMap()) +
            toolsOf(request)

    private fun messageOf(message: ChatMessage): Map<String, Any> =
        buildMap {
            put("role", message.role.code)
            put("content", message.content)
            if (message.toolCalls.isNotEmpty()) put("tool_calls", message.toolCalls.map(::callOf))
            message.toolCallId?.let { put("tool_call_id", it) }
            message.reasoning?.let { put("reasoning_content", it) }
        }

    private fun callOf(call: ToolCall): Map<String, Any> =
        mapOf(
            "id" to call.id,
            "type" to FUNCTION,
            "function" to mapOf("name" to call.name, "arguments" to call.arguments),
        )

    private fun toolsOf(request: ChatRequest): Map<String, Any> {
        if (request.tools.isEmpty()) return emptyMap()
        val tools =
            request.tools.map {
                val function =
                    mapOf(
                        "name" to it.name,
                        "description" to it.description,
                        "parameters" to json.readTree(it.parameters),
                    )
                mapOf("type" to FUNCTION, "function" to function)
            }
        return mapOf("tools" to tools, "tool_choice" to choiceOf(request.toolChoice))
    }

    private fun choiceOf(choice: ToolChoice): Any =
        when (choice) {
            ToolChoice.Auto -> "auto"
            ToolChoice.None -> "none"
            ToolChoice.Required -> "required"
            is ToolChoice.Named -> mapOf("type" to FUNCTION, "function" to mapOf("name" to choice.name))
        }

    private companion object {
        val JSON_OBJECT = mapOf("type" to "json_object")
        const val FUNCTION = "function"
    }
}
