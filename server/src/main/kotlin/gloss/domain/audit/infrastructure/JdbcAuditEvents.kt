package gloss.domain.audit.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcAuditEvents(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    fun append(entry: Entry) {
        jdbcClient
            .sql(
                """
                insert into audit_event (event_id, occurred_at, actor_id, subject_id, kind, detail)
                values (:eventId, :occurredAt, :actorId, :subjectId, :kind, cast(:detail as jsonb))
                on conflict (event_id) do nothing
                """.trimIndent(),
            ).param("eventId", entry.eventId)
            .param("occurredAt", Timestamp.from(entry.occurredAt))
            .param("actorId", entry.actorId)
            .param("subjectId", entry.subjectId)
            .param("kind", entry.kind)
            .param("detail", objectMapper.writeValueAsString(entry.detail))
            .update()
    }

    data class Entry(
        val eventId: UUID,
        val occurredAt: Instant,
        val actorId: UUID?,
        val subjectId: UUID?,
        val kind: String,
        val detail: Map<String, String>,
    )
}
