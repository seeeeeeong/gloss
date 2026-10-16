package demo.dto.response

import java.time.LocalDate

/** A nested type and a mapping function stay with the one response. */
data class UsageResponse(
    val spentMicros: Long,
    val month: Month,
) {
    data class Month(
        val start: LocalDate,
    )
}

fun Long.toUsage(start: LocalDate) = UsageResponse(this, UsageResponse.Month(start))
