package gloss.generation.application

import gloss.TestcontainersConfiguration
import gloss.account.Members
import gloss.generation.FakePlanHandler
import gloss.generation.domain.job.JobKind
import gloss.generation.domain.job.NewJob
import gloss.generation.infrastructure.JdbcJobQueue
import gloss.generation.newReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Not transactional: the runner commits its own transactions, and a write that fails must roll back for real.
@SpringBootTest
@Import(TestcontainersConfiguration::class, FakePlanHandler.Config::class)
class JobRunnerTest(
    @Autowired private val jobRunner: JobRunner,
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val fakePlanHandler: FakePlanHandler,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val clock: Clock,
    @Autowired passwordEncoder: PasswordEncoder,
) {
    private val account = Members(jdbc, passwordEncoder, clock).newReader(jdbc)

    private data class Row(
        val state: String,
        val errorKind: String?,
        val runAfter: Instant,
    )

    @BeforeEach
    @AfterEach
    fun emptyQueue() {
        jdbc.sql("delete from generation_job").update()
        fakePlanHandler.behaviour = { {} }
    }

    private fun claimed(kind: JobKind = JobKind.PLAN): RunningJob {
        val now = clock.instant()
        jdbcJobQueue.enqueue(
            NewJob(
                accountId = account,
                kind = kind,
                dedupeKey = "${kind.code}:${UUID.randomUUID()}",
                payload = mapOf(),
            ),
            now,
        )
        return RunningJob(checkNotNull(jdbcJobQueue.claim("worker-a", now, Duration.ofSeconds(60))))
    }

    private fun row(job: RunningJob): Row =
        jdbc
            .sql("select state, error_kind, run_after from generation_job where id = :id")
            .param("id", job.claimed.lease.jobId)
            .query { rs, _ -> Row(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()) }
            .single()

    private fun state(job: RunningJob) = row(job).state

    @Test
    fun `a job that succeeds is written in the transaction that marks it succeeded`() {
        val job = claimed()
        var seenInWrite: String? = null
        fakePlanHandler.behaviour = { { seenInWrite = state(job) } }

        jobRunner.run(job)

        assertThat(seenInWrite).isEqualTo("succeeded")
        assertThat(state(job)).isEqualTo("succeeded")
    }

    @Test
    fun `an attempt that lost its lease writes nothing and leaves the job to whoever holds it`() {
        val job = claimed()
        var written = false
        fakePlanHandler.behaviour = {
            jdbc
                .sql("update generation_job set lease_owner = 'worker-b' where id = :id")
                .param("id", job.claimed.lease.jobId)
                .update()
            val write = { written = true }
            write
        }

        jobRunner.run(job)

        assertThat(written).isFalse()
        assertThat(state(job)).isEqualTo("running")
    }

    @Test
    fun `an attempt the heartbeat found lost does not even try`() {
        val job = claimed()
        var written = false
        fakePlanHandler.behaviour = {
            job.lose()
            val write = { written = true }
            write
        }

        jobRunner.run(job)

        assertThat(written).isFalse()
        assertThat(state(job)).isEqualTo("running")
    }

    @Test
    fun `a job asked to stop ends cancelled, its work dropped`() {
        val job = claimed()
        var written = false
        fakePlanHandler.behaviour = {
            job.requestCancel()
            val write = { written = true }
            write
        }

        jobRunner.run(job)

        assertThat(written).isFalse()
        assertThat(state(job)).isEqualTo("cancelled")
    }

    @Test
    fun `a fault that may pass goes back to the queue for later`() {
        val job = claimed()
        fakePlanHandler.behaviour = { throw JobFailed("provider_busy", retryable = true) }

        jobRunner.run(job)

        val row = row(job)
        assertThat(row.state).isEqualTo("queued")
        assertThat(row.errorKind).isEqualTo("provider_busy")
        assertThat(row.runAfter).isAfterOrEqualTo(clock.instant().plus(Duration.ofSeconds(29)))
    }

    @Test
    fun `a fault on the last attempt fails the job`() {
        val first = claimed()
        jdbcJobQueue.retry(
            lease = first.claimed.lease,
            errorKind = "provider_busy",
            runAfter = clock.instant(),
            now = clock.instant(),
        )
        val second =
            RunningJob(checkNotNull(jdbcJobQueue.claim("worker-a", clock.instant(), Duration.ofSeconds(60))))
        fakePlanHandler.behaviour = { throw JobFailed("provider_busy", retryable = true) }

        jobRunner.run(second)

        assertThat(row(second)).extracting("state", "errorKind").containsExactly("failed", "provider_busy")
    }

    @Test
    fun `a fault that will not pass fails the job at once`() {
        val job = claimed()
        fakePlanHandler.behaviour = { throw JobFailed("cut_at_cap", retryable = false) }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "cut_at_cap")
    }

    @Test
    fun `an unexpected error fails the job`() {
        val job = claimed()
        fakePlanHandler.behaviour = { error("bug") }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "error")
    }

    @Test
    fun `a job asked to stop that then fails ends cancelled`() {
        val job = claimed()
        fakePlanHandler.behaviour = {
            job.requestCancel()
            throw JobFailed("provider_busy", retryable = true)
        }

        jobRunner.run(job)

        assertThat(state(job)).isEqualTo("cancelled")
    }

    @Test
    fun `a job no handler knows fails`() {
        val job = claimed(JobKind.CHECK)

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "no_handler")
    }

    @Test
    fun `a job whose writes fail ends failed, not left running to be run again`() {
        val job = claimed()
        fakePlanHandler.behaviour = { { error("constraint") } }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "write_failed")
    }

    @Test
    fun `a cancel the heartbeat has not yet passed on still wins over a success`() {
        val job = claimed()
        var written = false
        fakePlanHandler.behaviour = {
            jdbcJobQueue.cancelAll(account, clock.instant())
            val write = { written = true }
            write
        }

        jobRunner.run(job)

        assertThat(written).isFalse()
        assertThat(state(job)).isEqualTo("cancelled")
    }

    @Test
    fun `a cancel the heartbeat has not yet passed on stops a retry`() {
        val job = claimed()
        fakePlanHandler.behaviour = {
            jdbcJobQueue.cancelAll(account, clock.instant())
            throw JobFailed("provider_busy", retryable = true)
        }

        jobRunner.run(job)

        assertThat(state(job)).isEqualTo("cancelled")
    }

    @Test
    fun `a job of a reader disabled before it started is cancelled without running`() {
        val job = claimed()
        var ran = false
        fakePlanHandler.behaviour = {
            ran = true
            val write = {}
            write
        }
        jdbc
            .sql(
                "update account set status = 'disabled', disabled_at = now() where id = :id",
            ).param("id", account)
            .update()

        jobRunner.run(job)

        assertThat(ran).isFalse()
        assertThat(state(job)).isEqualTo("cancelled")
    }
}
