package gloss.generation.infrastructure

import gloss.common.util.UuidV7
import gloss.generation.domain.ClaimedJob
import gloss.generation.domain.JobEnding
import gloss.generation.domain.Lease
import gloss.generation.domain.LeaseRenewal
import gloss.generation.domain.job.JobKind
import gloss.generation.domain.job.NewJob
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The generation queue on PostgreSQL. A claim takes the oldest due job past rows other workers
 * hold, and every later write is fenced by the lease, so an attempt that lost its job to the
 * reaper cannot finish it a second time.
 */
@Repository
class JdbcJobQueue(
    private val jdbcClient: JdbcClient,
) {
    private val json = JsonMapper()

    /**
     * In the caller's transaction, so the job exists only if the caller's work commits. A live job
     * for the same key is returned instead of a second one.
     */
    fun enqueue(
        job: NewJob,
        now: Instant,
    ): UUID {
        repeat(ENQUEUE_TRIES) { _ ->
            insert(job, now)?.let { return it }
            // The live job may finish between the refused insert and this read; then insert again.
            liveJob(job)?.let { return it }
        }
        error("No live job for ${job.dedupeKey}, and none could be added")
    }

    private fun insert(
        job: NewJob,
        now: Instant,
    ): UUID? =
        jdbcClient
            .sql(
                """
                insert into generation_job (id, account_id, kind, dedupe_key, payload, state, run_after, created_at)
                values (:id, :account, :kind, :key, cast(:payload as jsonb), 'queued', now(), :now)
                on conflict (account_id, dedupe_key) where state in ('queued', 'running') do nothing
                returning id
                """.trimIndent(),
            ).param("id", UuidV7.at(now))
            .param("account", job.accountId)
            .param("kind", job.kind.code)
            .param("key", job.dedupeKey)
            .param("payload", json.writeValueAsString(job.payload))
            .param("now", Timestamp.from(now))
            .query(UUID::class.java)
            .optional()
            .orElse(null)

    private fun liveJob(job: NewJob): UUID? =
        jdbcClient
            .sql(
                """
                select id from generation_job
                where account_id = :account and dedupe_key = :key and state in ('queued', 'running')
                """.trimIndent(),
            ).param("account", job.accountId)
            .param("key", job.dedupeKey)
            .query(UUID::class.java)
            .optional()
            .orElse(null)

    /**
     * Holds the account's jobs until the transaction ends: an enqueue and a disabled account's cancel
     * take it, so the cancel sees every job the enqueue adds.
     */
    fun lockAccount(accountId: UUID) {
        jdbcClient
            .sql("select 1 from pg_advisory_xact_lock(hashtextextended('generation:' || cast(:id as text), 0))")
            .param("id", accountId)
            .query(Int::class.java)
            .single()
    }

    /**
     * The oldest due job, now running under [owner] for [lease]; null when none is due. Due times and
     * leases run on the database's clock, the one every instance and the reaper share.
     */
    fun claim(
        owner: String,
        now: Instant,
        lease: Duration,
    ): ClaimedJob? =
        jdbcClient
            .sql(
                """
                update generation_job j
                set state = 'running', attempt = j.attempt + 1, lease_owner = :owner,
                    lease_expires_at = now() + :lease * interval '1 millisecond',
                    started_at = coalesce(j.started_at, :now)
                from (select id from generation_job where state = 'queued' and run_after <= now()
                      order by run_after, id limit 1 for update skip locked) due
                where j.id = due.id
                returning j.id, j.attempt, j.lease_owner, j.account_id, j.kind, j.max_attempts, j.payload::text
                """.trimIndent(),
            ).param("owner", owner)
            .param("lease", lease.toMillis())
            .param("now", Timestamp.from(now))
            .query { rs, _ -> claimedOf(rs) }
            .optional()
            .orElse(null)

    /** Extends the lease by [renewFor], unless another attempt holds the job now. */
    fun heartbeat(
        lease: Lease,
        renewFor: Duration,
    ): LeaseRenewal =
        jdbcClient
            .sql(
                """
                update generation_job set lease_expires_at = now() + :renew * interval '1 millisecond'
                where $FENCE returning cancel_requested
                """.trimIndent(),
            ).param("renew", renewFor.toMillis())
            .params(fenceOf(lease))
            .query(Boolean::class.java)
            .optional()
            .map { cancel -> if (cancel) LeaseRenewal.CancelRequested else LeaseRenewal.Renewed }
            .orElse(LeaseRenewal.Lost)

    /**
     * Ends the attempt's job as [ending] says, or cancelled if a cancel was asked for meanwhile, even
     * one no heartbeat has passed on yet. Returns how it ended; null when the lease is lost.
     */
    fun finish(
        lease: Lease,
        ending: JobEnding,
        now: Instant,
    ): JobEnding? =
        jdbcClient
            .sql(
                """
                update generation_job
                set state = case when cancel_requested then 'cancelled' else :state end,
                    error_kind = case when cancel_requested then null else :error end,
                    finished_at = :now, lease_owner = null, lease_expires_at = null
                where $FENCE
                returning state, error_kind
                """.trimIndent(),
            ).param("state", ending.state)
            .param("error", (ending as? JobEnding.Failed)?.errorKind)
            .param("now", Timestamp.from(now))
            .params(fenceOf(lease))
            .query { rs, _ -> JobEnding.of(rs.getString("state"), rs.getString("error_kind")) }
            .optional()
            .orElse(null)

    /**
     * Puts the attempt's job back in the queue, due [delay] from now on the database's clock, or ends
     * it cancelled if a cancel was asked for meanwhile; false when the lease is lost.
     */
    fun retry(
        lease: Lease,
        errorKind: String,
        delay: Duration,
        now: Instant,
    ): Boolean =
        jdbcClient
            .sql(
                """
                update generation_job
                set state = case when cancel_requested then 'cancelled' else 'queued' end,
                    finished_at = case when cancel_requested then cast(:now as timestamptz) end,
                    error_kind = :error, run_after = now() + :delay * interval '1 millisecond',
                    lease_owner = null, lease_expires_at = null
                where $FENCE
                """.trimIndent(),
            ).param("error", errorKind)
            .param("delay", delay.toMillis())
            .param("now", Timestamp.from(now))
            .params(fenceOf(lease))
            .update() == 1

    /** Cancels the account's waiting jobs and asks its running ones to stop. */
    fun cancelAll(
        accountId: UUID,
        now: Instant,
    ): Int {
        val waiting =
            jdbcClient
                .sql(
                    """
                    update generation_job set state = 'cancelled', finished_at = :now
                    where account_id = :account and state = 'queued'
                    """.trimIndent(),
                ).param("account", accountId)
                .param("now", Timestamp.from(now))
                .update()
        // A job claimed after the first statement is running by this one, so none slips between.
        val running =
            jdbcClient
                .sql(
                    """
                    update generation_job set cancel_requested = true
                    where account_id = :account and state = 'running'
                    """.trimIndent(),
                ).param("account", accountId)
                .update()
        return waiting + running
    }

    private fun fenceOf(lease: Lease) = mapOf("id" to lease.jobId, "attempt" to lease.attempt, "owner" to lease.owner)

    private fun claimedOf(rs: ResultSet) =
        ClaimedJob(
            lease = Lease(rs.getObject("id", UUID::class.java), rs.getInt("attempt"), rs.getString("lease_owner")),
            accountId = rs.getObject("account_id", UUID::class.java),
            kind = JobKind.of(rs.getString("kind")),
            maxAttempts = rs.getInt("max_attempts"),
            payload = json.readValue(rs.getString("payload"), PAYLOAD),
        )

    private companion object {
        const val FENCE = "id = :id and attempt = :attempt and state = 'running' and lease_owner = :owner"

        // Each round needs the live job to end between two statements a millisecond apart; a third such
        // ending in a row is a bug to surface, not a race to wait out.
        const val ENQUEUE_TRIES = 3
        val PAYLOAD = object : TypeReference<Map<String, String>>() {}
    }
}
