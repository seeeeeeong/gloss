package gloss.domain.budget.infrastructure

import gloss.domain.budget.domain.LedgerRow
import gloss.domain.budget.infrastructure.JdbcUsageLedger.Companion.NOT_ENDED
import gloss.domain.budget.infrastructure.JdbcUsageLedger.Companion.SELECT
import gloss.domain.budget.infrastructure.JdbcUsageLedger.Companion.rowOf
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/** What the expiry round reads and moves: reservations left open, and how far it has looked. */
@Repository
class JdbcLedgerExpiry(
    private val jdbcClient: JdbcClient,
) {
    /** One instance expires at a time; the rest skip this round. */
    fun tryLockExpiry(): Boolean =
        jdbcClient
            .sql("select pg_try_advisory_xact_lock(hashtextextended('budget:expiry', 0))")
            .query(Boolean::class.java)
            .single()

    fun expiryWatermark(): Instant =
        jdbcClient
            .sql("select scanned_through from usage_ledger_expiry where id = 1")
            .query { rs, _ -> rs.getTimestamp(1).toInstant() }
            .single()

    fun advanceExpiryWatermark(to: Instant) {
        jdbcClient
            .sql("update usage_ledger_expiry set scanned_through = greatest(scanned_through, :to) where id = 1")
            .param("to", Timestamp.from(to))
            .update()
    }

    /**
     * Open reservations made from [since] to [before], oldest first. [since] is inclusive, so a
     * round that stopped mid-instant takes up the rest; one already ended is filtered out.
     */
    fun openBetween(
        since: Instant,
        before: Instant,
        limit: Int,
    ): List<LedgerRow> =
        jdbcClient
            .sql(
                """
                $SELECT r where r.kind = 'reserve' and r.occurred_at >= :since and r.occurred_at <= :before
                and $NOT_ENDED order by r.occurred_at limit :limit
                """.trimIndent(),
            ).param("since", Timestamp.from(since))
            .param("before", Timestamp.from(before))
            .param("limit", limit)
            .query { rs, _ -> rowOf(rs) }
            .list()
}
