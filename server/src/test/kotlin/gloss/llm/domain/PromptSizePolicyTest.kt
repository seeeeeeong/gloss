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
}
