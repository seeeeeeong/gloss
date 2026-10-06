package gloss.budget.application

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.account.Members
import gloss.budget.application.dto.LimitRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class ReservationExpiryTest(
    @Autowired private val budgetService: BudgetService,
    @Autowired private val reservationExpiry: ReservationExpiry,
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

    @Test
    fun `a reservation left open half an hour expires spent, and a late settle is ignored`() {
        val account = account()
        val reservation = reserve(account)

        clock.advance(Duration.ofMinutes(31))
        val expired = reservationExpiry.expireStale()

        assertThat(expired).isGreaterThanOrEqualTo(1)
        assertThat(budgetService.usage(account).spentMicros).isEqualTo(980)
        assertThat(budgetService.settle(reservation, report)).isFalse()
    }

    @Test
    fun `a reservation left open through a ten-day outage still expires when the job is back`() {
        val account = account()
        reserve(account)

        clock.advance(Duration.ofDays(10))
        reservationExpiry.expireStale()

        assertThat(budgetService.usage(account).inFlightMicros).isZero()
    }
}
