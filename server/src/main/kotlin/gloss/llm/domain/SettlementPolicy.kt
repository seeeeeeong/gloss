package gloss.llm.domain

/**
 * What a call that did not finish is charged on. Before any text it may still have billed its
 * prompt; after some, all it was allowed. Without the provider's usage the most it could have
 * cost is kept, and only a call that cannot have billed is released.
 */
object SettlementPolicy {
    fun decide(
        usageReported: Boolean,
        outputStarted: Boolean,
        promptMayHaveBilled: Boolean,
    ): SettlementDecision =
        when {
            usageReported -> SettlementDecision.Reported
            outputStarted -> SettlementDecision.FullEstimate
            promptMayHaveBilled -> SettlementDecision.PromptEstimate
            else -> SettlementDecision.Release
        }
}

sealed interface SettlementDecision {
    /** Settled with what the provider reported. */
    data object Reported : SettlementDecision

    /** Settled at the whole prompt and the whole cap, marked estimated. */
    data object FullEstimate : SettlementDecision

    /** Settled at the whole prompt and no output, marked estimated. */
    data object PromptEstimate : SettlementDecision

    /** Nothing can have billed: the reservation is released. */
    data object Release : SettlementDecision
}
