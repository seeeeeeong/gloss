package gloss.budget.application

import java.util.UUID

/** [maxTokens] is the cap sent with the call, so the reservation covers the most it can cost. */
data class ReserveRequest(
    val accountId: UUID,
    val apiKeyId: UUID?,
    val purpose: Purpose,
    val model: String,
    val prices: Prices,
    val promptTokens: Long,
    val maxTokens: Long,
)
