package gloss.budget.application

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.account.Members
import gloss.budget.application.dto.LimitRequest
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class BudgetServiceTest(
    @Autowired private val budgetService: BudgetService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    // µUSD per million tokens; made up, so a test reads the arithmetic, not a price list.
    private val prices = Prices(hitPerMillion = 28_000, missPerMillion = 280_000, outPerMillion = 420_000)

    @BeforeEach
    fun resetClock() {
        // Mid-month in Seoul, well away from a boundary unless a test moves it there.
        clock.set(Instant.parse("2026-10-15T03:00:00Z"))
    }

    private fun account(limitMicros: Long? = 10_000): UUID {
        val id =
            jdbc
                .sql("select id from account where login_id = :loginId")
                .param("loginId", members.create())
                .query(UUID::class.java)
                .single()
        if (limitMicros != null) budgetService.setLimit(id, LimitRequest(limitMicros))
        return id
    }

    // 2000 prompt tokens as misses and 1000 output tokens: 560 + 420 = 980 µUSD held.
    private fun reserve(account: UUID) =
        budgetService.reserve(
            ReserveRequest(
                accountId = account,
                apiKeyId = null,
                purpose = Purpose.CHAT,
                model = "model",
                prices = prices,
                promptTokens = 2_000,
                maxTokens = 1_000,
            ),
        )

    // 1000 hits, 1000 misses, 500 out: 28 + 280 + 210 = 518 µUSD spent.
    private val report =
        UsageReport(cacheHitTokens = 1_000, cacheMissTokens = 1_000, completionTokens = 500, outcome = Outcome.STOP)

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    @Test
    fun `nothing is reserved until the reader sets a monthly limit`() {
        assertFails(BudgetError.LIMIT_NOT_SET) { reserve(account(limitMicros = null)) }
    }

    @Test
    fun `a reservation holds the worst case, and settling leaves what the call cost`() {
        val account = account()

        val reservation = reserve(account)
        val held = budgetService.usage(account)
        budgetService.settle(reservation, report)
        val settled = budgetService.usage(account)

        assertThat(reservation.amountMicros).isEqualTo(980)
        assertThat(held.inFlightMicros to held.spentMicros).isEqualTo(980L to 0L)
        assertThat(settled.inFlightMicros to settled.spentMicros).isEqualTo(0L to 518L)
        assertThat(settled.remainingMicros).isEqualTo(10_000L - 518)
    }

    @Test
    fun `a reservation one micro-dollar past the limit is refused`() {
        val account = account(limitMicros = 979)

        assertFails(BudgetError.LIMIT_REACHED) { reserve(account) }
    }

    @Test
    fun `a reservation ends once, so a second settle or a late release changes nothing`() {
        val account = account()
        val reservation = reserve(account)

        assertThat(budgetService.settle(reservation, report)).isTrue()
        assertThat(budgetService.settle(reservation, report)).isFalse()
        assertThat(budgetService.release(reservation, Outcome.ERROR)).isFalse()
        assertThat(budgetService.usage(account).spentMicros).isEqualTo(518)
    }

    @Test
    fun `another account's reservation cannot be ended from here`() {
        val owner = account()
        val reservation = reserve(owner)
        val stranger = reservation.copy(accountId = account())

        assertThatThrownBy {
            budgetService.release(
                stranger,
                Outcome.ERROR,
            )
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(budgetService.usage(owner).inFlightMicros).isEqualTo(980)
    }

    @Test
    fun `a released reservation gives everything back`() {
        val account = account()

        budgetService.release(reserve(account), Outcome.ERROR)

        assertThat(budgetService.usage(account).spentMicros).isZero()
    }

    @Test
    fun `a call that cost more than the limit is recorded whole, and the next one is refused`() {
        val account = account(limitMicros = 1_000)
        val expensive = report.copy(completionTokens = 5_000)

        budgetService.settle(reserve(account), expensive)

        assertThat(budgetService.usage(account).spentMicros).isEqualTo(28L + 280 + 2_100)
        assertFails(BudgetError.LIMIT_REACHED) { reserve(account) }
    }

    @Test
    fun `too many at once is refused until one ends`() {
        val account = account(limitMicros = 1_000_000)
        val open = List(4) { reserve(account) }

        assertFails(BudgetError.TOO_MANY_IN_FLIGHT) { reserve(account) }
        budgetService.settle(open.first(), report)
        assertThat(reserve(account).amountMicros).isEqualTo(980)
    }

    @Test
    fun `too many in a minute is refused until the minute has passed`() {
        val account = account(limitMicros = 1_000_000)
        repeat(30) { budgetService.settle(reserve(account), report) }

        assertFails(BudgetError.TOO_FREQUENT) { reserve(account) }
        clock.advance(Duration.ofMinutes(1))
        assertThat(reserve(account).amountMicros).isEqualTo(980)
    }

    @Test
    fun `a new month starts from nothing, and an ending after midnight counts toward its own month`() {
        val account = account(limitMicros = 1_000)
        clock.set(Instant.parse("2026-10-31T14:59:00Z"))
        val lastOfOctober = reserve(account)

        clock.set(Instant.parse("2026-10-31T15:01:00Z"))
        budgetService.settle(lastOfOctober, report)

        assertThat(budgetService.usage(account).spentMicros).isZero()
        assertThat(reserve(account).amountMicros).isEqualTo(980)
    }

    @Test
    fun `a limit is a positive amount up to a thousand dollars`() {
        val account = account(limitMicros = null)

        assertFails(BudgetError.INVALID_LIMIT) { budgetService.setLimit(account, LimitRequest(0)) }
        assertFails(BudgetError.INVALID_LIMIT) { budgetService.setLimit(account, LimitRequest(1_000_000_001)) }
        assertThat(budgetService.setLimit(account, LimitRequest(5_000_000)).limitMicros).isEqualTo(5_000_000)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `twenty reservations at once for one account never take more than its limit`() {
        // Room for exactly three; without the account's lock several would read the same total.
        val account = account(limitMicros = 3 * 980L)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(20)
        try {
            val tries =
                List(20) {
                    pool.submit<Boolean> {
                        start.await()
                        runCatching { reserve(account) }.isSuccess
                    }
                }
            start.countDown()

            assertThat(tries.count { it.get() }).isEqualTo(3)
            assertThat(budgetService.usage(account).inFlightMicros).isEqualTo(3 * 980L)
        } finally {
            pool.shutdown()
            // Committed, so cleared: tests sharing this database list every account. The ledger
            // is append-only and holds no key to it.
            jdbc.sql("delete from spending_limit where account_id = :id").param("id", account).update()
            jdbc.sql("delete from account where id = :id").param("id", account).update()
        }
    }
}
