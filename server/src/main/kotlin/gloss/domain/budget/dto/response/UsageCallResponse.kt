package gloss.domain.budget.dto.response

import gloss.domain.budget.domain.UsageCall
import java.time.Instant

/** µUSD, estimated as [UsageResponse] is; the token counts are null for a call that expired. */
data class UsageCallResponse(
    val endedAt: Instant,
    val purpose: String,
    val costMicros: Long,
    val cacheHitTokens: Long?,
    val cacheMissTokens: Long?,
    val completionTokens: Long?,
) {
    companion object {
        fun from(call: UsageCall) =
            UsageCallResponse(
                endedAt = call.endedAt,
                purpose = call.purpose,
                costMicros = call.costMicros,
                cacheHitTokens = call.tokens?.hit,
                cacheMissTokens = call.tokens?.miss,
                completionTokens = call.tokens?.out,
            )
    }
}
