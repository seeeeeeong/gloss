// A controller's requests and responses in one file on purpose: the fixture the dto rule has to catch.

package demo.dto

data class LimitRequest(
    val monthlyLimitMicros: Long,
)

data class UsageResponse(
    val spentMicros: Long,
)
