package gloss.domain.budget.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BillingMonthPolicyTest {
    @Test
    fun `the month is Seoul's, so 9 am on the last day in UTC is already next month`() {
        assertThat(
            BillingMonthPolicy.monthOf(Instant.parse("2026-10-31T14:59:59Z")),
        ).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(
            BillingMonthPolicy.monthOf(Instant.parse("2026-10-31T15:00:00Z")),
        ).isEqualTo(LocalDate.of(2026, 11, 1))
    }
}
