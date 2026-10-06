package gloss.budget.application.dto

/** µUSD: a reader's $5 is 5,000,000. */
data class LimitRequest(
    val monthlyLimitMicros: Long,
)
