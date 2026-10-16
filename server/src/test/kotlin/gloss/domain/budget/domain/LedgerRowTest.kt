package gloss.domain.budget.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LedgerRowTest {
    private val reservedAt = Instant.parse("2026-10-10T00:00:00Z")
    private val endedAt = reservedAt.plusSeconds(20)

    private val reserve =
        LedgerRow(
            occurredAt = reservedAt,
            billingMonth = LocalDate.parse("2026-10-01"),
            accountId = UUID.randomUUID(),
            apiKeyId = UUID.randomUUID(),
            reservationId = UUID.randomUUID(),
            kind = LedgerKind.RESERVE,
            amountMicros = 1_000,
            purpose = "chat",
            model = "deepseek-flash",
            rates = CostPolicy.Rates(hit = 0, miss = 1_000_000, out = 0),
        )

    @Test
    fun `a settle row is the change from the reservation to what the call cost, at the reservation's rates`() {
        val usage =
            LedgerRow.Usage(
                CostPolicy.Tokens(hit = 0, miss = 300, out = 0),
                reasoningTokens = 5,
                estimated = false,
            )

        val settled = reserve.settled(endedAt, usage, outcome = "stop")

        assertThat(settled.kind).isEqualTo(LedgerKind.SETTLE)
        assertThat(reserve.amountMicros + settled.amountMicros).isEqualTo(300)
        assertThat(settled.tokens).isEqualTo(usage.tokens)
        assertThat(settled.reasoningTokens).isEqualTo(5)
        assertThat(settled.occurredAt).isEqualTo(endedAt)
        assertThat(settled.reservationId).isEqualTo(reserve.reservationId)
    }

    @Test
    fun `a call that cost more than reserved settles above the reservation`() {
        val usage =
            LedgerRow.Usage(
                CostPolicy.Tokens(hit = 0, miss = 1_500, out = 0),
                reasoningTokens = null,
                estimated = true,
            )

        val settled = reserve.settled(endedAt, usage, outcome = "error")

        assertThat(settled.amountMicros).isEqualTo(500)
        assertThat(settled.usageEstimated).isTrue()
    }

    @Test
    fun `a release gives the reservation back whole`() {
        val released = reserve.released(endedAt, outcome = "error")

        assertThat(released.kind).isEqualTo(LedgerKind.RELEASE)
        assertThat(reserve.amountMicros + released.amountMicros).isZero()
    }

    @Test
    fun `an expiry keeps what was reserved spent`() {
        val expired = reserve.expired(endedAt, outcome = "expired")

        assertThat(expired.kind).isEqualTo(LedgerKind.EXPIRE)
        assertThat(expired.amountMicros).isZero()
    }

    @Test
    fun `only a reservation is ended`() {
        val released = reserve.released(endedAt, outcome = "error")

        assertThatThrownBy {
            released.released(
                endedAt,
                outcome = "error",
            )
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
