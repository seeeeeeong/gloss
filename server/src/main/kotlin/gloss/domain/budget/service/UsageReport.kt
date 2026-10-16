package gloss.domain.budget.service

/** What the provider reported, or an estimate marked as one when the call ended without usage. */
data class UsageReport(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val outcome: Outcome,
    val reasoningTokens: Long? = null,
    val usageEstimated: Boolean = false,
)

/** How a call ended, from the provider's finish_reason or from how it was cut off. */
enum class Outcome(
    val code: String,
) {
    STOP("stop"),
    LENGTH("length"),
    CONTENT_FILTER("content_filter"),
    TOOL_CALLS("tool_calls"),
    CANCELLED_BEFORE_OUTPUT("cancelled_before_output"),
    CANCELLED_AFTER_OUTPUT("cancelled_after_output"),
    ERROR("error"),
}
