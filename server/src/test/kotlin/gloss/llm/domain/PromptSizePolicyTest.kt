package gloss.llm.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptSizePolicyTest {
    @Test
    fun `a prompt is counted as at most one token a byte, with room for each message's framing and the call's`() {
        assertThat(PromptSizePolicy.upperBoundTokens(listOf("hello"), thinking = false)).isEqualTo(5L + 8 + 32)
    }

    @Test
    fun `korean counts three bytes a character, so the bound never falls short of the tokenizer`() {
        assertThat(
            PromptSizePolicy.upperBoundTokens(listOf("가나", "a"), thinking = false),
        ).isEqualTo(6L + 1 + 2 * 8 + 32)
    }

    @Test
    fun `thinking adds the reasoning header the template puts in front`() {
        assertThat(PromptSizePolicy.upperBoundTokens(listOf("hello"), thinking = true))
            .isEqualTo(PromptSizePolicy.upperBoundTokens(listOf("hello"), thinking = false) + 256)
    }

    @Test
    fun `a cap is kept when it fits under the model's ceiling and the context left`() {
        assertThat(fit(requested = 4_000, promptTokens = 1_000)).isEqualTo(4_000)
    }

    @Test
    fun `a cap above the model's ceiling is brought down to it`() {
        assertThat(fit(requested = 50_000, promptTokens = 1_000)).isEqualTo(MAX_OUTPUT)
    }

    @Test
    fun `a cap is lowered to what the prompt leaves of the context`() {
        assertThat(fit(requested = 8_000, promptTokens = CONTEXT - 300L)).isEqualTo(300)
    }

    @Test
    fun `a prompt that fills the context leaves no cap`() {
        assertThat(fit(requested = 8_000, promptTokens = CONTEXT.toLong())).isNull()
    }

    @Test
    fun `a cap below one is raised to one`() {
        assertThat(fit(requested = 0, promptTokens = 1_000)).isEqualTo(1)
    }

    private fun fit(
        requested: Int,
        promptTokens: Long,
    ) = PromptSizePolicy.fittedCap(
        requested = requested,
        promptTokens = promptTokens,
        contextTokens = CONTEXT,
        maxOutputTokens = MAX_OUTPUT,
    )

    private companion object {
        const val CONTEXT = 100_000
        const val MAX_OUTPUT = 16_000
    }
}
