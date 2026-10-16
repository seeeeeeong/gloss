package gloss.domain.budget.dto.request

/** µUSD: a reader's $5 is 5,000,000. */
data class LimitRequest(
    val monthlyLimitMicros: Long,
)
