package gloss.domain.setup.infrastructure

import gloss.common.util.UuidV7
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupKind
import gloss.domain.setup.domain.SetupMessage
import gloss.domain.setup.domain.SetupSession
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** A setup session's messages. Append-only: what was said stays said. */
@Repository
class JdbcSetupMessages(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) {
    /** Numbered after the session's last; the caller holds the session's lock, so none comes in between. */
    fun append(
        session: SetupSession,
        content: SetupContent,
        at: Instant,
    ) {
        val kind = SetupKind.of(content)
        jdbcClient
            .sql(
                """
                insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
                select :id, :session, :account, coalesce(max(seq), 0) + 1, :role, :kind, cast(:payload as jsonb), :at
                from setup_message
                where session_id = :session
                """.trimIndent(),
            ).param("id", UuidV7.at(at))
            .param("session", session.id)
            .param("account", session.accountId)
            .param("role", kind.role.code)
            .param("kind", kind.code)
            .param("payload", objectMapper.writeValueAsString(content))
            .param("at", Timestamp.from(at))
            .update()
    }

    fun list(
        sessionId: UUID,
        accountId: UUID,
    ): List<SetupMessage> =
        jdbcClient
            .sql(
                """
                select seq, kind, payload::text as payload, created_at
                from setup_message
                where session_id = :session and account_id = :account
                order by seq
                """.trimIndent(),
            ).param("session", sessionId)
            .param("account", accountId)
            .query { row, _ -> messageOf(row) }
            .list()

    private fun messageOf(row: ResultSet): SetupMessage {
        val kind = SetupKind.of(row.getString("kind"))
        return SetupMessage(
            seq = row.getInt("seq"),
            content = objectMapper.readValue(row.getString("payload"), kind.type),
            createdAt = row.getTimestamp("created_at").toInstant(),
        )
    }
}
