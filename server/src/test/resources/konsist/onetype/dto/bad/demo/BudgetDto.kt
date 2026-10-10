// Outside a dto package on purpose: the fixture the file-name rule has to catch.

package demo

data class LimitRequest(
    val monthlyLimitMicros: Long,
)

data class UsageResponse(
    val spentMicros: Long,
)
