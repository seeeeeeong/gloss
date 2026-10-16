package gloss.domain.budget.domain

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
) {
    /** What a call used, priced at its reservation's rates when it is settled. */
    data class Usage(
        val tokens: CostPolicy.Tokens,
        val reasoningTokens: Long?,
        val estimated: Boolean,
    )

    // An ending is a change to its reservation: the two rows sum to what the call cost.
    fun settled(
        at: Instant,
        usage: Usage,
        outcome: String,
    ): LedgerRow {
        requireReserve()
        return copy(
            occurredAt = at,
            kind = LedgerKind.SETTLE,
            amountMicros = CostPolicy.usageMicros(usage.tokens, rates) - amountMicros,
            tokens = usage.tokens,
            reasoningTokens = usage.reasoningTokens,
            outcome = outcome,
            usageEstimated = usage.estimated,
        )
    }

    /** For a call that cannot have billed: the reservation is given back whole. */
    fun released(
        at: Instant,
        outcome: String,
    ): LedgerRow {
        requireReserve()
        return copy(occurredAt = at, kind = LedgerKind.RELEASE, amountMicros = -amountMicros, outcome = outcome)
    }

    /** What it held stays spent, since the call may have run; it only stops counting as in flight. */
    fun expired(
        at: Instant,
        outcome: String,
    ): LedgerRow {
        requireReserve()
        return copy(occurredAt = at, kind = LedgerKind.EXPIRE, amountMicros = 0, outcome = outcome)
    }

    private fun requireReserve() = check(kind == LedgerKind.RESERVE) { "Only a reservation is ended" }
}

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
