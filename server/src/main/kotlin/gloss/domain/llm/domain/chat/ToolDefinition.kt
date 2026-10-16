package gloss.domain.llm.domain.chat

/**
 * A function the model may call. [parameters] is its JSON Schema object, as text. Kept the same
 * from call to call: the definitions go in front of the conversation, so one that changes is
 * likely a new prompt prefix and a cache miss.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String,
)

/** Whether the model must call a tool. DeepSeek refuses [Required] and [Named] with thinking on. */
sealed interface ToolChoice {
    data object Auto : ToolChoice

    data object None : ToolChoice

    data object Required : ToolChoice

    data class Named(
        val name: String,
    ) : ToolChoice
}

/**
 * A call the model made, as it wrote it. [arguments] is the raw text, which the provider warns
 * may not be valid JSON; [parsedArguments] says whether it is.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
) {
    fun parsedArguments(): ToolArgumentsParser.Result = ToolArgumentsParser.parse(arguments)
}
