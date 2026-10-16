package gloss.domain.budget.domain

import java.time.Instant

/** A call that ended at a cost: its reservation and ending summed. No tokens for one that expired. */
data class UsageCall(
    val endedAt: Instant,
    val purpose: String,
    val costMicros: Long,
    val tokens: CostPolicy.Tokens?,
)
