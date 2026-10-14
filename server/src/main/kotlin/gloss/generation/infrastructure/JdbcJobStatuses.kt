package gloss.generation.infrastructure

import gloss.generation.domain.job.JobState
import gloss.generation.domain.job.JobStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** How jobs stand, for the reader who asked for them. Reads only. */
@Repository
class JdbcJobStatuses(
    private val jdbcClient: JdbcClient,
) {
    /** The newest job a reader has for [dedupeKey], whatever its state; null when there never was one. */
    fun latest(
        accountId: UUID,
        dedupeKey: String,
    ): JobStatus? =
        jdbcClient
            .sql(
                """
                select state, error_kind from generation_job
                where account_id = :account and dedupe_key = :key
                order by created_at desc, id desc limit 1
                """.trimIndent(),
            ).param("account", accountId)
            .param("key", dedupeKey)
            .query { rs, _ -> JobStatus(JobState.of(rs.getString("state")), rs.getString("error_kind")) }
            .optional()
            .orElse(null)
}
