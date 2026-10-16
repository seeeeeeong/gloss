package gloss.domain.budget.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object BillingMonthPolicy {
    // Readers are in Korea; a month ends at their midnight, not UTC's.
    private val SEOUL = ZoneId.of("Asia/Seoul")

    fun monthOf(at: Instant): LocalDate = at.atZone(SEOUL).toLocalDate().withDayOfMonth(1)
}
