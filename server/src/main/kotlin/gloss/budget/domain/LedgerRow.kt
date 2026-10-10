package gloss.budget.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One usage_ledger row. An ending copies its reservation's month, purpose, model and rates. */
data class LedgerRow(
    val occurredAt: Instant,
    val billingMonth: LocalDate,
    val accountId: UUID,
    val apiKeyId: UUID?,
    val reservationId: UUID,
    val kind: LedgerKind,
    val amountMicros: Long,
    val purpose: String,
    val model: String,
    val rates: CostPolicy.Rates,
    val tokens: CostPolicy.Tokens? = null,
    val reasoningTokens: Long? = null,
    val outcome: String? = null,
    val usageEstimated: Boolean = false,
)

enum class LedgerKind(
    val code: String,
) {
    RESERVE("reserve"),
    SETTLE("settle"),
    RELEASE("release"),
    EXPIRE("expire"),
    ;

    companion object {
        fun of(code: String): LedgerKind = entries.first { it.code == code }
    }
}
