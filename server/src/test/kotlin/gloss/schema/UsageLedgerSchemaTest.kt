package gloss.schema

import gloss.TestcontainersConfiguration
import gloss.account.Members
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class UsageLedgerSchemaTest(
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private data class Row(
        val reservationId: UUID,
        val kind: String = "reserve",
        val amount: Long = 100,
        val outcome: String? = null,
        val month: LocalDate = LocalDate.of(2026, 10, 1),
    )

    private fun append(row: Row) =
        jdbc
            .sql(
                """
                insert into usage_ledger (occurred_at, billing_month, account_id, reservation_id, kind, amount_micros,
                                          purpose, model, price_hit, price_miss, price_out, outcome)
                values (now(), :month, :account, :reservation, :kind, :amount, 'chat', 'model', 1, 2, 3, :outcome)
                """.trimIndent(),
            ).param("month", row.month)
            .param("account", UUID.randomUUID())
            .param("reservation", row.reservationId)
            .param("kind", row.kind)
            .param("amount", row.amount)
            .param("outcome", row.outcome)
            .update()

    @Test
    fun `a reservation ends once, whichever way it ends`() {
        val reservation = UUID.randomUUID()
        append(Row(reservation))
        append(Row(reservationId = reservation, kind = "settle", amount = -20, outcome = "stop"))

        assertThatThrownBy {
            append(
                Row(reservationId = reservation, kind = "release", amount = -100, outcome = "error"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a reservation is reserved once`() {
        val reservation = UUID.randomUUID()
        append(Row(reservation))

        assertThatThrownBy { append(Row(reservation)) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a reservation holds a positive amount`() {
        assertThatThrownBy { append(Row(UUID.randomUUID(), amount = 0)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a release only gives money back`() {
        assertThatThrownBy {
            append(
                Row(reservationId = UUID.randomUUID(), kind = "release", amount = 5, outcome = "error"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an expiry moves no money, so what was reserved stays spent`() {
        assertThatThrownBy {
            append(
                Row(reservationId = UUID.randomUUID(), kind = "expire", amount = 5, outcome = "expired"),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a reservation says nothing of how it ended`() {
        assertThatThrownBy { append(Row(UUID.randomUUID(), outcome = "stop")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an ending says how it ended`() {
        assertThatThrownBy { append(Row(UUID.randomUUID(), kind = "settle", amount = 1)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a row belongs to a month by its first day`() {
        assertThatThrownBy { append(Row(UUID.randomUUID(), month = LocalDate.of(2026, 10, 2))) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a recorded row cannot be changed`() {
        append(Row(UUID.randomUUID()))

        assertThatThrownBy { jdbc.sql("update usage_ledger set amount_micros = 1").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }

    @Test
    fun `a recorded row cannot be deleted`() {
        append(Row(UUID.randomUUID()))

        assertThatThrownBy { jdbc.sql("delete from usage_ledger").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }

    private fun setLimit(micros: Long) {
        val account =
            jdbc
                .sql("select id from account where login_id = :loginId")
                .param("loginId", members.create())
                .query(UUID::class.java)
                .single()
        jdbc
            .sql(
                """
                insert into spending_limit (account_id, version, monthly_limit_micros, updated_at)
                values (:account, 0, :micros, now())
                """.trimIndent(),
            ).param("account", account)
            .param("micros", micros)
            .update()
    }

    @Test
    fun `a monthly limit of up to a thousand dollars is kept`() {
        assertThatCode { setLimit(1_000_000_000) }.doesNotThrowAnyException()
    }

    @Test
    fun `a monthly limit of nothing is refused`() {
        assertThatThrownBy { setLimit(0) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a monthly limit past a thousand dollars is refused, as a typo`() {
        assertThatThrownBy { setLimit(1_000_000_001) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
