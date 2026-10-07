package gloss.llm.domain

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

    fun upperBoundTokens(
        contents: List<String>,
        thinking: Boolean,
    ): Long =
        contents.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + FRAMING_PER_MESSAGE } +
            FRAMING_PER_CALL +
            if (thinking) THINKING_HEADER else 0
}
