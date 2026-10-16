package gloss.domain.audit.infrastructure

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class JdbcAuditEventsTest(
    @Autowired private val jdbcAuditEvents: JdbcAuditEvents,
    @Autowired private val jdbc: JdbcClient,
) {
    private val entry =
        JdbcAuditEvents.Entry(
            eventId = UUID.randomUUID(),
            occurredAt = Instant.parse("2026-10-05T00:00:00Z"),
            actorId = null,
            subjectId = UUID.randomUUID(),
            kind = "sign_in_failed",
            detail = mapOf("ip" to "203.0.113.7", "reason" to "wrong_password"),
        )

    private fun detailsOf(subjectId: UUID?): List<String?> =
        jdbc
            .sql("select detail::text from audit_event where subject_id = :id")
            .param("id", subjectId)
            .query(String::class.java)
            .list()

    @Test
    fun `an entry is stored with its detail as a JSON object`() {
        jdbcAuditEvents.append(entry)

        assertThat(detailsOf(entry.subjectId)).containsExactly("""{"ip": "203.0.113.7", "reason": "wrong_password"}""")
    }

    @Test
    fun `an event delivered again is stored once`() {
        jdbcAuditEvents.append(entry)
        jdbcAuditEvents.append(entry)

        assertThat(detailsOf(entry.subjectId)).hasSize(1)
    }
}
