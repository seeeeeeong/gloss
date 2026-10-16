package gloss.domain.budget.dto.response

import java.time.LocalDate

/**
 * µUSD, estimated from token counts at documented prices: DeepSeek bills the reader's own key,
 * so its invoice is the real figure. [remainingMicros] is null until a limit is set.
 */
data class UsageResponse(
    val billingMonth: LocalDate,
    val limitMicros: Long?,
    val spentMicros: Long,
    val inFlightMicros: Long,
    val remainingMicros: Long?,
)
