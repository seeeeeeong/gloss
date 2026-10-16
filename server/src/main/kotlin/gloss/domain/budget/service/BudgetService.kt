package gloss.domain.budget.service

import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.budget.domain.BillingMonthPolicy
import gloss.domain.budget.domain.CostPolicy
import gloss.domain.budget.domain.LedgerKind
import gloss.domain.budget.domain.LedgerRow
import gloss.domain.budget.domain.SpendPolicy
import gloss.domain.budget.domain.SpendingLimit
import gloss.domain.budget.domain.repository.SpendingLimitRepository
import gloss.domain.budget.dto.request.LimitRequest
import gloss.domain.budget.dto.response.UsageCallResponse
import gloss.domain.budget.dto.response.UsageResponse
import gloss.domain.budget.infrastructure.JdbcUsageLedger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Every call on a reader's key is reserved here first and ended here after: settled with what
 * it cost, released if it failed, or expired if no one came back. The ledger's sum is the truth.
 */
@Service
class BudgetService(
    private val jdbcUsageLedger: JdbcUsageLedger,
    private val spendingLimitRepository: SpendingLimitRepository,
    private val clock: Clock,
    @Value("\${gloss.budget.in-flight}") inFlight: Int,
    @Value("\${gloss.budget.per-minute}") perMinute: Int,
    @Value("\${gloss.budget.per-hour}") perHour: Int,
) {
    private val caps = SpendPolicy.Caps(inFlight = inFlight, perMinute = perMinute, perHour = perHour)

    @Transactional
    fun reserve(request: ReserveRequest): Reservation {
        val rates = request.prices.rates()
        val amount = CostPolicy.reserveMicros(request.promptTokens, request.maxTokens, rates)
        jdbcUsageLedger.lock(request.accountId)
        // Read after the lock: a request that waited on it is stamped when it goes out, so the
        // rate, the month and the expiry watermark all see it where it belongs.
        val now = clock.instant()
        val month = BillingMonthPolicy.monthOf(now)
        val spend =
            SpendPolicy.Spend(
                limit = limitOf(request.accountId),
                spent = jdbcUsageLedger.monthTotal(request.accountId, month),
                inFlight = jdbcUsageLedger.openSince(request.accountId, now.minus(EXPIRY)),
                lastMinute = jdbcUsageLedger.reservesSince(request.accountId, now.minus(MINUTE)),
                lastHour = jdbcUsageLedger.reservesSince(request.accountId, now.minus(HOUR)),
            )
        val error = BudgetError.of(SpendPolicy.decide(spend, amount, caps))
        if (error != null) throw GlossException(error)
        val id = UuidV7.at(now)
        jdbcUsageLedger.append(
            LedgerRow(
                occurredAt = now,
                billingMonth = month,
                accountId = request.accountId,
                apiKeyId = request.apiKeyId,
                reservationId = id,
                kind = LedgerKind.RESERVE,
                amountMicros = amount,
                purpose = request.purpose.code,
                model = request.model,
                rates = rates,
            ),
        )
        return Reservation(id = id, accountId = request.accountId, amountMicros = amount)
    }

    /**
     * Records what the call cost, even past the limit: it was spent, and the next reservation is
     * refused. False when the reservation had already ended, e.g. expired while the call ran.
     */
    @Transactional
    fun settle(
        reservation: Reservation,
        report: UsageReport,
    ): Boolean {
        val usage =
            LedgerRow.Usage(
                tokens = CostPolicy.Tokens(report.cacheHitTokens, report.cacheMissTokens, report.completionTokens),
                reasoningTokens = report.reasoningTokens,
                estimated = report.usageEstimated,
            )
        val settled = reserveOf(reservation).settled(clock.instant(), usage, report.outcome.code)
        return jdbcUsageLedger.append(settled)
    }

    /** For a call that failed before it could cost anything. False when it had already ended. */
    @Transactional
    fun release(
        reservation: Reservation,
        outcome: Outcome,
    ): Boolean {
        val released = reserveOf(reservation).released(clock.instant(), outcome.code)
        return jdbcUsageLedger.append(released)
    }

    @Transactional(readOnly = true)
    fun usage(accountId: UUID): UsageResponse {
        val month = BillingMonthPolicy.monthOf(clock.instant())
        val (total, open) = jdbcUsageLedger.monthUsage(accountId, month)
        val limit = limitOf(accountId)
        return UsageResponse(
            billingMonth = month,
            limitMicros = limit,
            spentMicros = total - open,
            inFlightMicros = open,
            remainingMicros = limit?.let { it - total },
        )
    }

    @Transactional(readOnly = true)
    fun recentCalls(accountId: UUID): List<UsageCallResponse> {
        val month = BillingMonthPolicy.monthOf(clock.instant())
        return jdbcUsageLedger.recentCalls(accountId, month, RECENT_CALLS).map(UsageCallResponse::from)
    }

    @Transactional
    fun setLimit(
        accountId: UUID,
        request: LimitRequest,
    ): UsageResponse {
        val micros = request.monthlyLimitMicros
        if (micros !in 1..MAX_LIMIT) throw GlossException(BudgetError.INVALID_LIMIT)
        jdbcUsageLedger.lock(accountId)
        val now = clock.instant()
        val existing = spendingLimitRepository.findById(accountId).orElse(null)
        if (existing == null) {
            val limit = SpendingLimit.create(accountId, micros, now)
            spendingLimitRepository.save(limit)
        } else {
            existing.change(micros, now)
        }
        spendingLimitRepository.flush()
        return usage(accountId)
    }

    // Only within its own account: a reservation id from elsewhere finds nothing.
    private fun reserveOf(reservation: Reservation): LedgerRow =
        checkNotNull(jdbcUsageLedger.findReserve(reservation.accountId, reservation.id)) {
            "No reservation ${reservation.id} for this account"
        }

    private fun limitOf(accountId: UUID): Long? =
        spendingLimitRepository.findById(accountId).map { it.monthlyLimitMicros }.orElse(null)

    private fun Prices.rates() = CostPolicy.Rates(hit = hitPerMillion, miss = missPerMillion, out = outPerMillion)

    internal companion object {
        val MINUTE: Duration = Duration.ofMinutes(1)
        val HOUR: Duration = Duration.ofHours(1)

        // Longer than any call runs, generation included; after it a reservation is a crash's leftover.
        internal val EXPIRY: Duration = Duration.ofMinutes(30)

        // $1,000: matches spending_limit's check; a larger number is far more likely a typo.
        const val MAX_LIMIT = 1_000_000_000L

        // A page's worth: the month's total is above it, this only shows where it went lately.
        const val RECENT_CALLS = 50
    }
}
