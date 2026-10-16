package gloss.domain.budget.service

import gloss.domain.budget.infrastructure.JdbcLedgerExpiry
import gloss.domain.budget.infrastructure.JdbcUsageLedger
import gloss.domain.budget.service.BudgetService.Companion.EXPIRY
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class ReservationExpiry(
    private val jdbcUsageLedger: JdbcUsageLedger,
    private val jdbcLedgerExpiry: JdbcLedgerExpiry,
    private val clock: Clock,
) {
    /** A reservation nobody ended in [EXPIRY]: the process died mid-call. */
    @Transactional
    fun expireStale(): Int {
        if (!jdbcLedgerExpiry.tryLockExpiry()) return 0
        val now = clock.instant()
        val before = now.minus(EXPIRY)
        val open = jdbcLedgerExpiry.openBetween(jdbcLedgerExpiry.expiryWatermark(), before, EXPIRY_BATCH)
        open.forEach { reserve ->
            jdbcUsageLedger.append(reserve.expired(now, EXPIRED))
        }
        // A full batch stops at its last row, where the next round resumes; otherwise all up to
        // [before] has ended.
        jdbcLedgerExpiry.advanceExpiryWatermark(if (open.size == EXPIRY_BATCH) open.last().occurredAt else before)
        return open.size
    }

    private companion object {
        // One round's share: with each reader capped at four in flight, a crash leaves few, and the
        // next minute takes any rest; it keeps one round to one short transaction.
        const val EXPIRY_BATCH = 500
        const val EXPIRED = "expired"
    }
}
