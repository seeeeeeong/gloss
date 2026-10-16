package gloss.domain.generation.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/** What the reaper reads and moves: jobs whose lease ran out with nobody renewing it. */
@Repository
class JdbcJobReap(
    private val jdbcClient: JdbcClient,
) {
    /** One instance reaps at a time; the rest skip this round. Needs a transaction. */
    fun tryLockReap(): Boolean =
        jdbcClient
            .sql("select pg_try_advisory_xact_lock(hashtextextended('generation:reap', 0))")
            .query(Boolean::class.java)
            .single()

    /**
     * Jobs whose lease ran out on the database's clock: their worker died. Each goes back to the queue, or ends failed
     * once it has used its attempts, or cancelled if a cancel was asked for.
     */
    fun reap(
        now: Instant,
        limit: Int,
    ): Int =
        jdbcClient
            .sql(
                """
                update generation_job j
                set state = case when j.cancel_requested then 'cancelled'
                                 when j.attempt >= j.max_attempts then 'failed' else 'queued' end,
                    finished_at = case when j.cancel_requested or j.attempt >= j.max_attempts then cast(:now as timestamptz) end,
                    error_kind = 'lease_lost', run_after = now(), lease_owner = null, lease_expires_at = null
                from (select id from generation_job where state = 'running' and lease_expires_at < now()
                      order by lease_expires_at limit :limit for update skip locked) lost
                where j.id = lost.id
                """.trimIndent(),
            ).param("now", Timestamp.from(now))
            .param("limit", limit)
            .update()
}
