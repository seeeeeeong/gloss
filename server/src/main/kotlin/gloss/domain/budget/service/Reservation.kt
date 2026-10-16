package gloss.domain.budget.service

import java.util.UUID

/** Handed back to settle or release it; the account is checked against the ledger's row. */
data class Reservation(
    val id: UUID,
    val accountId: UUID,
    val amountMicros: Long,
)

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

/** µUSD per million tokens, as the provider documents them for the model in use. */
data class Prices(
    val hitPerMillion: Long,
    val missPerMillion: Long,
    val outPerMillion: Long,
)

enum class Purpose(
    val code: String,
) {
    CHAT("chat"),
    PLAN("plan"),
    POST("post"),
    CHECK("check"),
}
