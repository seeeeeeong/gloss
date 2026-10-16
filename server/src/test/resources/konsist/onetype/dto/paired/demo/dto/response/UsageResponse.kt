// Named after its first type, but a second rides along on purpose: the fixture the dto rule has to catch.

package demo.dto.response

data class UsageResponse(
    val spentMicros: Long,
    val limit: LimitResponse,
)

data class LimitResponse(
    val monthlyLimitMicros: Long,
)
