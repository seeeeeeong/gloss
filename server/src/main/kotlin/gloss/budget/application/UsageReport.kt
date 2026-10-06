package gloss.budget.application

/** What the provider reported, or an estimate marked as one when the call ended without usage. */
data class UsageReport(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val outcome: Outcome,
    val reasoningTokens: Long? = null,
    val usageEstimated: Boolean = false,
)
