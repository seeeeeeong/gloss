package gloss.budget.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CostPolicyTest {
    // µUSD per million tokens.
    private val rates = CostPolicy.Rates(hit = 28_000, miss = 280_000, out = 420_000)

    @Test
    fun `usage costs each kind of token at its own rate, rounded up once`() {
        val cost = CostPolicy.usageMicros(CostPolicy.Tokens(hit = 1_000, miss = 2_000, out = 500), rates)

        // 1000 × 0.028 + 2000 × 0.28 + 500 × 0.42 = 28 + 560 + 210
        assertThat(cost).isEqualTo(798)
    }

    @Test
    fun `a fraction of a micro-dollar is charged as a whole one`() {
        assertThat(CostPolicy.usageMicros(CostPolicy.Tokens(hit = 1, miss = 0, out = 0), rates)).isEqualTo(1)
        assertThat(CostPolicy.usageMicros(CostPolicy.Tokens(hit = 0, miss = 0, out = 0), rates)).isZero()
    }

    @Test
    fun `a reservation takes every prompt token as a miss and every allowed output token as spent`() {
        assertThat(CostPolicy.reserveMicros(promptTokens = 2_000, maxTokens = 1_000, rates = rates))
            .isEqualTo(560 + 420)
    }
}
