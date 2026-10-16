package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class LedgerToolCallsOutcomeSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun append(
        reservation: UUID,
        kind: String,
        amount: Long,
        outcome: String?,
    ) = jdbc
        .sql(
            """
            insert into usage_ledger (occurred_at, billing_month, account_id, reservation_id, kind, amount_micros,
                                      purpose, model, price_hit, price_miss, price_out, outcome)
            values (now(), '2026-10-01', :account, :reservation, :kind, :amount, 'chat', 'model', 1, 2, 3, :outcome)
            """.trimIndent(),
        ).param("account", UUID.randomUUID())
        .param("reservation", reservation)
        .param("kind", kind)
        .param("amount", amount)
        .param("outcome", outcome)
        .update()

    private fun settled(outcome: String): UUID {
        val reservation = UUID.randomUUID()
        append(reservation = reservation, kind = "reserve", amount = 100, outcome = null)
        append(reservation = reservation, kind = "settle", amount = -20, outcome = outcome)
        return reservation
    }

    private fun outcomeOf(reservation: UUID): String =
        jdbc
            .sql("select outcome from usage_ledger where reservation_id = :id and kind = 'settle'")
            .param("id", reservation)
            .query(String::class.java)
            .single()

    @Test
    fun `a call that ended for tool calls is recorded as such`() {
        assertThatCode { settled("tool_calls") }.doesNotThrowAnyException()
    }

    @Test
    fun `rows written before keep how they ended`() {
        val stopped = settled("stop")
        val expired = UUID.randomUUID()
        append(reservation = expired, kind = "reserve", amount = 100, outcome = null)
        append(reservation = expired, kind = "expire", amount = 0, outcome = "expired")

        jdbc
            .sql(
                ClassPathResource("db/migration/V16__ledger_tool_calls_outcome.sql").getContentAsString(Charsets.UTF_8),
            ).update()

        assertThat(outcomeOf(stopped)).isEqualTo("stop")
        assertThat(
            jdbc
                .sql("select outcome from usage_ledger where reservation_id = :id and kind = 'expire'")
                .param("id", expired)
                .query(String::class.java)
                .single(),
        ).isEqualTo("expired")
    }

    @Test
    fun `an outcome the ledger does not know is still refused`() {
        assertThatThrownBy { settled("tool_call") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
