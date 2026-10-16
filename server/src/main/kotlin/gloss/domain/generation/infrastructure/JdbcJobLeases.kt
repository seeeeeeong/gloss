package gloss.domain.generation.infrastructure

import gloss.domain.generation.domain.Lease
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** An attempt's hold on its job, for a write another module fences by it. */
@Repository
class JdbcJobLeases(
    private val jdbcClient: JdbcClient,
) {
    /** Whether the attempt still holds its job, locked so until the caller's transaction ends. */
    fun hold(lease: Lease): Boolean =
        jdbcClient
            .sql(
                """
                select 1 from generation_job
                where id = :id and attempt = :attempt and state = 'running' and lease_owner = :owner
                for share
                """.trimIndent(),
            ).param("id", lease.jobId)
            .param("attempt", lease.attempt)
            .param("owner", lease.owner)
            .query(Int::class.java)
            .optional()
            .isPresent
}
