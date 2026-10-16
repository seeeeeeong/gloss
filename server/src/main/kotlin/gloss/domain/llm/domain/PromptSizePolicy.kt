package gloss.domain.llm.domain

/**
 * How many prompt tokens a call can bill at most, without the provider's tokenizer: a byte-level
 * BPE token covers at least one byte, so the UTF-8 length bounds the count. A reservation sized
 * from it is never short; settling brings it to what the provider reports.
 */
object PromptSizePolicy {
    // The role and separator tokens around each message; a handful in every chat template.
    private const val FRAMING_PER_MESSAGE = 8L

    // The template's own start and end tokens, once a call.
    private const val FRAMING_PER_CALL = 32L

    // In thinking mode DeepSeek's template puts a reasoning header in front of the prompt
    // (deepseek-recipe, the v4 renderer); a few dozen tokens, held at a generous 256.
    private const val THINKING_HEADER = 256L

    // DeepSeek's open encoding (DeepSeek-V3.2 encoding_dsv32.py) puts about 1.2 KB of tool
    // instructions in front of the definitions; held at 2,048.
    private const val TOOLS_HEADER = 2_048L

    // The same encoding wraps each call in invoke and parameter tags, a few dozen bytes a parameter.
    private const val FRAMING_PER_TOOL_CALL = 256L

    fun upperBoundTokens(
        contents: List<String>,
        thinking: Boolean,
    ): Long =
        contents.sumOf { bytes(it) + FRAMING_PER_MESSAGE } +
            FRAMING_PER_CALL +
            if (thinking) THINKING_HEADER else 0

    /**
     * What tools add: the template's instructions when [definitions] are offered, each definition,
     * and each call in the conversation. Both are counted twice, since the template writes JSON
     * again with a space after every ':' and ','; nothing when there are neither.
     */
    fun toolTokens(
        definitions: List<String>,
        calls: List<String>,
    ): Long =
        (if (definitions.isEmpty()) 0 else TOOLS_HEADER) +
            definitions.sumOf { 2 * bytes(it) + FRAMING_PER_MESSAGE } +
            calls.sumOf { 2 * bytes(it) + FRAMING_PER_TOOL_CALL }

    private fun bytes(text: String): Long = text.toByteArray(Charsets.UTF_8).size.toLong()

    /**
     * The cap, within the model's output ceiling and what the prompt leaves of the context; null
     * when the prompt leaves nothing.
     */
    fun fittedCap(
        requested: Int,
        promptTokens: Long,
        contextTokens: Int,
        maxOutputTokens: Int,
    ): Int? {
        val room = contextTokens - promptTokens
        if (room < 1) return null
        return requested.coerceIn(1, maxOutputTokens).coerceAtMost(room.toInt())
    }
}
