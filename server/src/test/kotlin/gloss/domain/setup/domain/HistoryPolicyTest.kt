package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HistoryPolicyTest {
    @Test
    fun `every turn is kept while all fit the budget`() {
        assertThat(HistoryPolicy.start(List(10) { 3_000L })).isZero()
    }

    @Test
    fun `past the budget the oldest go, down to half of it`() {
        // Eleven of 3,000 pass 32,000: the oldest go until 15,000 or less is left, five turns.
        assertThat(HistoryPolicy.start(List(11) { 3_000L })).isEqualTo(6)
    }

    @Test
    fun `the turns kept stay the same until the budget is reached again`() {
        val starts = (11..16).map { turns -> HistoryPolicy.start(List(turns) { 3_000L }) }

        assertThat(starts).containsExactly(6, 6, 6, 6, 6, 6)
        assertThat(HistoryPolicy.start(List(17) { 3_000L })).isGreaterThan(6)
    }

    @Test
    fun `a single turn larger than the budget is not kept`() {
        assertThat(HistoryPolicy.start(listOf(1_000L, 40_000L))).isEqualTo(2)
    }
}
