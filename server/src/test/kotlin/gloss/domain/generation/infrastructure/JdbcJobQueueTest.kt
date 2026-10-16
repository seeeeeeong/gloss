package gloss.domain.generation.infrastructure

import gloss.TestcontainersConfiguration
import gloss.domain.generation.domain.JobEnding
import gloss.domain.generation.domain.Lease
import gloss.domain.generation.domain.LeaseRenewal
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class JdbcJobQueueTest(
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val jdbc: JdbcClient,
) {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val lease = Duration.ofSeconds(60)
    private val account = UUID.randomUUID()

    private data class Row(
        val state: String,
        val attempt: Int,
        val errorKind: String?,
        val finished: Boolean,
        val cancelRequested: Boolean,
    )

    // Only this test's jobs: the rest of the table is hidden until the transaction rolls back.
    @BeforeEach
    fun emptyQueue() {
        jdbc.sql("delete from generation_job").update()
    }

    private fun job(key: String = "post:${UUID.randomUUID()}") =
        NewJob(accountId = account, kind = JobKind.POST, dedupeKey = key, payload = mapOf("item" to "1.2"))

    private fun claim(at: Instant = now) = jdbcJobQueue.claim(owner = "worker-a", now = at, lease = lease)

    // Due times run on the database's clock, so a test moves a job's due time there rather than a clock.
    private fun dueIn(
        id: UUID,
        delay: Duration,
    ) {
        jdbc
            .sql("update generation_job set run_after = now() + :ms * interval '1 millisecond' where id = :id")
            .param("ms", delay.toMillis())
            .param("id", id)
            .update()
    }

    private fun secondsUntilDue(id: UUID): Double =
        jdbc
            .sql("select extract(epoch from run_after - now()) from generation_job where id = :id")
            .param("id", id)
            .query(Double::class.java)
            .single()

    private fun row(id: UUID): Row =
        jdbc
            .sql(
                """
                select state, attempt, error_kind, finished_at is not null as finished, cancel_requested
                from generation_job where id = :id
                """.trimIndent(),
            ).param("id", id)
            .query { rs, _ ->
                Row(
                    state = rs.getString("state"),
                    attempt = rs.getInt("attempt"),
                    errorKind = rs.getString("error_kind"),
                    finished = rs.getBoolean("finished"),
                    cancelRequested = rs.getBoolean("cancel_requested"),
                )
            }.single()

    @Test
    fun `a second request for live work gets the first job back`() {
        val first = jdbcJobQueue.enqueue(job("plan:toc-1"), now)

        assertThat(jdbcJobQueue.enqueue(job("plan:toc-1"), now)).isEqualTo(first)
    }

    @Test
    fun `the same work for another reader is that reader's own job`() {
        val first = jdbcJobQueue.enqueue(job("plan:toc-1"), now)

        assertThat(jdbcJobQueue.enqueue(job("plan:toc-1").copy(accountId = UUID.randomUUID()), now))
            .isNotEqualTo(first)
    }

    @Test
    fun `a job asked to stop ends cancelled, however its attempt meant to end it`() {
        val succeeding = jdbcJobQueue.enqueue(job(), now)
        val first = checkNotNull(claim())
        val retrying = jdbcJobQueue.enqueue(job(), now)
        val second = checkNotNull(claim())
        jdbcJobQueue.cancelAll(account, now)

        assertThat(jdbcJobQueue.finish(first.lease, JobEnding.Succeeded, now)).isEqualTo(JobEnding.Cancelled)
        assertThat(
            jdbcJobQueue.retry(
                lease = second.lease,
                errorKind = "provider_busy",
                delay = Duration.ofSeconds(30),
                now = now,
            ),
        ).isTrue()

        assertThat(row(succeeding).state).isEqualTo("cancelled")
        assertThat(row(retrying).state).isEqualTo("cancelled")
        assertThat(row(retrying).finished).isTrue()
    }

    @Test
    fun `work asked for again after its job ended is a new job`() {
        val first = jdbcJobQueue.enqueue(job("plan:toc-1"), now)
        val claimed = checkNotNull(claim())
        jdbcJobQueue.finish(claimed.lease, JobEnding.Succeeded, now)

        assertThat(jdbcJobQueue.enqueue(job("plan:toc-1"), now)).isNotEqualTo(first)
    }

    @Test
    fun `a claim takes the oldest due job, with its payload, and leases it`() {
        val older = jdbcJobQueue.enqueue(job(), now)
        jdbcJobQueue.enqueue(job(), now.plusSeconds(1))

        val claimed = checkNotNull(claim(now.plusSeconds(5)))

        assertThat(claimed.lease).isEqualTo(Lease(older, attempt = 1, owner = "worker-a"))
        assertThat(claimed.accountId).isEqualTo(account)
        assertThat(claimed.kind).isEqualTo(JobKind.POST)
        assertThat(claimed.payload).isEqualTo(mapOf("item" to "1.2"))
        assertThat(row(older).state).isEqualTo("running")
    }

    @Test
    fun `a job is not claimed before it is due, nor twice`() {
        val id = jdbcJobQueue.enqueue(job(), now)
        dueIn(id, Duration.ofSeconds(30))

        assertThat(claim()).isNull()
        dueIn(id, Duration.ZERO)
        assertThat(claim()).isNotNull()
        assertThat(claim()).isNull()
    }

    @Test
    fun `when a job is due is the database's clock, whatever an instance's clock says`() {
        jdbcJobQueue.enqueue(job(), now.plus(Duration.ofDays(1)))

        assertThat(claim(now.minus(Duration.ofDays(1)))).isNotNull()
    }

    @Test
    fun `a heartbeat renews only the attempt that holds the job, and passes on a cancel`() {
        jdbcJobQueue.enqueue(job(), now)
        val claimed = checkNotNull(claim())

        assertThat(jdbcJobQueue.heartbeat(claimed.lease, lease)).isEqualTo(LeaseRenewal.Renewed)
        assertThat(jdbcJobQueue.heartbeat(claimed.lease.copy(attempt = 2), lease)).isEqualTo(LeaseRenewal.Lost)
        assertThat(jdbcJobQueue.heartbeat(claimed.lease.copy(owner = "worker-b"), lease)).isEqualTo(LeaseRenewal.Lost)

        jdbcJobQueue.cancelAll(account, now)

        assertThat(jdbcJobQueue.heartbeat(claimed.lease, lease)).isEqualTo(LeaseRenewal.CancelRequested)
    }

    @Test
    fun `only the attempt that holds the job ends it`() {
        val id = jdbcJobQueue.enqueue(job(), now)
        val claimed = checkNotNull(claim())

        assertThat(jdbcJobQueue.finish(claimed.lease.copy(attempt = 2), JobEnding.Succeeded, now)).isNull()
        assertThat(jdbcJobQueue.finish(claimed.lease, JobEnding.Failed("provider_failed"), now))
            .isEqualTo(JobEnding.Failed("provider_failed"))
        assertThat(jdbcJobQueue.finish(claimed.lease, JobEnding.Succeeded, now)).isNull()
        assertThat(row(id)).isEqualTo(
            Row(
                state = "failed",
                attempt = 1,
                errorKind = "provider_failed",
                finished = true,
                cancelRequested = false,
            ),
        )
    }

    @Test
    fun `a retried job waits in the queue until it is due again`() {
        val id = jdbcJobQueue.enqueue(job(), now)
        val claimed = checkNotNull(claim())

        assertThat(
            jdbcJobQueue.retry(
                lease = claimed.lease,
                errorKind = "provider_busy",
                delay = Duration.ofSeconds(30),
                now = now,
            ),
        ).isTrue()

        assertThat(row(id).state).isEqualTo("queued")
        assertThat(secondsUntilDue(id)).isEqualTo(30.0)
        assertThat(claim()).isNull()
        dueIn(id, Duration.ZERO)
        assertThat(checkNotNull(claim()).lease.attempt).isEqualTo(2)
    }

    @Test
    fun `cancelling an account ends its waiting jobs and asks its running ones to stop`() {
        val running = jdbcJobQueue.enqueue(job(), now)
        checkNotNull(claim())
        val waiting = jdbcJobQueue.enqueue(job(), now)
        val someoneElses = jdbcJobQueue.enqueue(job().copy(accountId = UUID.randomUUID()), now)

        assertThat(jdbcJobQueue.cancelAll(account, now)).isEqualTo(2)

        assertThat(row(waiting).state).isEqualTo("cancelled")
        assertThat(row(running).state).isEqualTo("running")
        assertThat(row(running).cancelRequested).isTrue()
        assertThat(row(someoneElses).state).isEqualTo("queued")
    }

    private fun secondsLeftOnLease(id: UUID): Long =
        jdbc
            .sql("select extract(epoch from lease_expires_at - now())::bigint from generation_job where id = :id")
            .param("id", id)
            .query(Long::class.java)
            .single()

    @Test
    fun `a lease runs on the database's clock, whatever the worker's says`() {
        val farAhead = Instant.parse("2030-01-01T00:00:00Z")
        val id = jdbcJobQueue.enqueue(job(), farAhead)
        val claimed = checkNotNull(claim(farAhead))

        assertThat(secondsLeftOnLease(id)).isEqualTo(60)

        jdbc.sql("update generation_job set lease_expires_at = now() where id = :id").param("id", id).update()
        jdbcJobQueue.heartbeat(claimed.lease, lease)

        assertThat(secondsLeftOnLease(id)).isEqualTo(60)
    }

    @Test
    fun `a retry that meets a cancel ends when it is made, not when it would have run`() {
        val id = jdbcJobQueue.enqueue(job(), now)
        val claimed = checkNotNull(claim())
        jdbcJobQueue.cancelAll(account, now)

        jdbcJobQueue.retry(
            lease = claimed.lease,
            errorKind = "provider_busy",
            delay = Duration.ofSeconds(30),
            now = now,
        )

        val finishedAt =
            jdbc
                .sql("select finished_at from generation_job where id = :id")
                .param("id", id)
                .query { rs, _ -> rs.getTimestamp(1).toInstant() }
                .single()
        assertThat(finishedAt).isEqualTo(now)
    }
}
