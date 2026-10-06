package gloss.budget.infrastructure

import gloss.budget.domain.CostPolicy
import gloss.budget.domain.LedgerKind
import gloss.budget.domain.LedgerRow
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcUsageLedger(
    private val jdbcClient: JdbcClient,
) {
    /**
     * Held until commit: an account's reservations are judged one at a time, so two read the
     * same total and both fit is impossible. Other accounts never wait on it.
     */
    fun lock(accountId: UUID) {
        jdbcClient
            .sql("select 1 from pg_advisory_xact_lock(hashtextextended('budget:' || cast(:id as text), 0))")
            .param("id", accountId)
            .query(Int::class.java)
            .single()
    }

    /** False when the reservation had already ended. */
    fun append(row: LedgerRow): Boolean =
        jdbcClient
            .sql(
                """
                insert into usage_ledger (occurred_at, billing_month, account_id, api_key_id, reservation_id, kind,
                                          amount_micros, purpose, model, cache_hit_tokens, cache_miss_tokens,
                                          completion_tokens, reasoning_tokens, price_hit, price_miss, price_out,
                                          outcome, usage_estimated)
                values (:occurredAt, :month, :accountId, :apiKeyId, :reservationId, :kind, :amount, :purpose, :model,
                        :hit, :miss, :out, :reasoning, :priceHit, :priceMiss, :priceOut, :outcome, :estimated)
                on conflict (reservation_id) where kind <> 'reserve' do nothing
                """.trimIndent(),
            ).param("occurredAt", Timestamp.from(row.occurredAt))
            .param("month", row.billingMonth)
            .param("accountId", row.accountId)
            .param("apiKeyId", row.apiKeyId)
            .param("reservationId", row.reservationId)
            .param("kind", row.kind.code)
            .param("amount", row.amountMicros)
            .param("purpose", row.purpose)
            .param("model", row.model)
            .param("hit", row.tokens?.hit)
            .param("miss", row.tokens?.miss)
            .param("out", row.tokens?.out)
            .param("reasoning", row.reasoningTokens)
            .param("priceHit", row.rates.hit)
            .param("priceMiss", row.rates.miss)
            .param("priceOut", row.rates.out)
            .param("outcome", row.outcome)
            .param("estimated", row.usageEstimated)
            .update() == 1

    fun findReserve(
        accountId: UUID,
        reservationId: UUID,
    ): LedgerRow? =
        jdbcClient
            .sql("$SELECT where account_id = :accountId and reservation_id = :id and kind = 'reserve'")
            .param("accountId", accountId)
            .param("id", reservationId)
            .query { rs, _ -> rowOf(rs) }
            .optional()
            .orElse(null)

    /** Reserves minus releases plus settle differences: what the month has spent or holds. */
    fun monthTotal(
        accountId: UUID,
        month: LocalDate,
    ): Long =
        jdbcClient
            .sql(
                """
                select coalesce(sum(amount_micros), 0) from usage_ledger
                where account_id = :id and billing_month = :month
                """.trimIndent(),
            ).param("id", accountId)
            .param("month", month)
            .query(Long::class.java)
            .single()

    /**
     * The month's total and what its open reservations hold, read in one statement so both come
     * from the same moment: a settle committing between two reads would show neither state.
     */
    fun monthUsage(
        accountId: UUID,
        month: LocalDate,
    ): Pair<Long, Long> =
        jdbcClient
            .sql(
                """
                select coalesce(sum(r.amount_micros), 0),
                       coalesce(sum(r.amount_micros) filter (where r.kind = 'reserve' and $NOT_ENDED), 0)
                from usage_ledger r
                where r.account_id = :id and r.billing_month = :month
                """.trimIndent(),
            ).param("id", accountId)
            .param("month", month)
            .query { rs, _ -> rs.getLong(1) to rs.getLong(2) }
            .single()

    fun openSince(
        accountId: UUID,
        since: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                select count(*) from usage_ledger r
                where r.account_id = :id and r.kind = 'reserve' and r.occurred_at > :since and $NOT_ENDED
                """.trimIndent(),
            ).param("id", accountId)
            .param("since", Timestamp.from(since))
            .query(Int::class.java)
            .single()

    fun reservesSince(
        accountId: UUID,
        since: Instant,
    ): Int =
        jdbcClient
            .sql(
                """
                select count(*) from usage_ledger
                where account_id = :id and kind = 'reserve' and occurred_at > :since
                """.trimIndent(),
            ).param("id", accountId)
            .param("since", Timestamp.from(since))
            .query(Int::class.java)
            .single()

    internal companion object {
        internal fun rowOf(rs: ResultSet) =
            LedgerRow(
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                billingMonth = rs.getObject("billing_month", LocalDate::class.java),
                accountId = rs.getObject("account_id", UUID::class.java),
                apiKeyId = rs.getObject("api_key_id", UUID::class.java),
                reservationId = rs.getObject("reservation_id", UUID::class.java),
                kind = LedgerKind.of(rs.getString("kind")),
                amountMicros = rs.getLong("amount_micros"),
                purpose = rs.getString("purpose"),
                model = rs.getString("model"),
                rates = CostPolicy.Rates(rs.getLong("price_hit"), rs.getLong("price_miss"), rs.getLong("price_out")),
            )

        const val SELECT = "select * from usage_ledger"
        const val NOT_ENDED =
            "not exists (select 1 from usage_ledger e where e.reservation_id = r.reservation_id and e.kind <> 'reserve')"
    }
}
