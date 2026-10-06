package gloss.budget.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpendPolicyTest {
    private val caps = SpendPolicy.Caps(inFlight = 2, perMinute = 3, perHour = 10)

    private fun spend(
        limit: Long? = 1_000,
        spent: Long = 0,
        inFlight: Int = 0,
        lastMinute: Int = 0,
        lastHour: Int = 0,
    ) = SpendPolicy.Spend(
        limit = limit,
        spent = spent,
        inFlight = inFlight,
        lastMinute = lastMinute,
        lastHour = lastHour,
    )

    @Test
    fun `a reservation that fits under the limit and the caps is allowed`() {
        assertThat(SpendPolicy.decide(spend(spent = 400), amount = 600, caps = caps)).isEqualTo(SpendDecision.Allowed)
    }

    @Test
    fun `without a limit nothing is spent`() {
        assertThat(
            SpendPolicy.decide(spend(limit = null), amount = 1, caps = caps),
        ).isEqualTo(SpendDecision.LimitNotSet)
    }

    @Test
    fun `one micro-dollar past the limit is refused`() {
        assertThat(
            SpendPolicy.decide(spend(spent = 400), amount = 601, caps = caps),
        ).isEqualTo(SpendDecision.LimitReached)
    }

    @Test
    fun `too many at once, in a minute or in an hour is refused, before the money is looked at`() {
        assertThat(
            SpendPolicy.decide(spend(inFlight = 2), amount = 1, caps = caps),
        ).isEqualTo(SpendDecision.TooManyInFlight)
        assertThat(
            SpendPolicy.decide(spend(lastMinute = 3), amount = 1, caps = caps),
        ).isEqualTo(SpendDecision.TooFrequent)
        assertThat(
            SpendPolicy.decide(spend(lastHour = 10), amount = 1, caps = caps),
        ).isEqualTo(SpendDecision.TooFrequent)
    }
}
