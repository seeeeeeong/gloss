package demo.dto

data class LimitRequest(
    val monthlyLimitMicros: Long,
)

data class UsageResponse(
    val spentMicros: Long,
)
