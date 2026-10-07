package gloss.llm.domain

/** As the provider reported it: hit and miss both, never one derived from the other. */
data class TokenUsage(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val reasoningTokens: Long?,
)
