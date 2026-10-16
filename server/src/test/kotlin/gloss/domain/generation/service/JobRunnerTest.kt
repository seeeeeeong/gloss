package gloss.domain.generation.service

import gloss.TestcontainersConfiguration
import gloss.domain.account.Members
import gloss.domain.account.service.AccountService
import gloss.domain.generation.FakeJobHandler
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.infrastructure.JdbcJobQueue
import gloss.domain.generation.newReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.stream.Stream

// Not transactional: the runner commits its own transactions, and a write that fails must roll back for real.
@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeJobHandler.Config::class)
class JobRunnerTest(
    @Autowired private val jobRunner: JobRunner,
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val fakeJobHandler: FakeJobHandler,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val clock: Clock,
    @Autowired passwordEncoder: PasswordEncoder,
) {
    private val account = Members(jdbc, passwordEncoder, clock).newReader(jdbc)

    private object NoHandlers : ObjectProvider<JobHandler> {
        override fun stream(): Stream<JobHandler> = Stream.empty()
    }

    private data class Row(
        val state: String,
        val errorKind: String?,
        val secondsUntilDue: Double,
    )

    @BeforeEach
    @AfterEach
    fun emptyQueue() {
        jdbc.sql("delete from generation_job").update()
        fakeJobHandler.behaviour = { {} }
    }

    private fun claimed(kind: JobKind = JobKind.CHECK): RunningJob {
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
            .sql("select state, error_kind, extract(epoch from run_after - now()) from generation_job where id = :id")
            .param("id", job.claimed.lease.jobId)
            .query { rs, _ -> Row(rs.getString(1), rs.getString(2), rs.getDouble(3)) }
            .single()

    private fun state(job: RunningJob) = row(job).state

    @Test
    fun `a job that succeeds is written in the transaction that marks it succeeded`() {
        val job = claimed()
        var seenInWrite: String? = null
        fakeJobHandler.behaviour = { { seenInWrite = state(job) } }

        jobRunner.run(job)

        assertThat(seenInWrite).isEqualTo("succeeded")
        assertThat(state(job)).isEqualTo("succeeded")
    }

    @Test
    fun `an attempt that lost its lease writes nothing and leaves the job to whoever holds it`() {
        val job = claimed()
        var written = false
        fakeJobHandler.behaviour = {
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
        fakeJobHandler.behaviour = {
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
        fakeJobHandler.behaviour = {
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
        fakeJobHandler.behaviour = { throw JobFailed("provider_busy", retryable = true) }

        jobRunner.run(job)

        val row = row(job)
        assertThat(row.state).isEqualTo("queued")
        assertThat(row.errorKind).isEqualTo("provider_busy")
        // Due on the database's clock, the backoff's 30 seconds from the retry.
        assertThat(row.secondsUntilDue).isGreaterThanOrEqualTo(29.0)
    }

    @Test
    fun `a fault on the last attempt fails the job`() {
        val first = claimed()
        jdbcJobQueue.retry(
            lease = first.claimed.lease,
            errorKind = "provider_busy",
            delay = Duration.ZERO,
            now = clock.instant(),
        )
        val second =
            RunningJob(checkNotNull(jdbcJobQueue.claim("worker-a", clock.instant(), Duration.ofSeconds(60))))
        fakeJobHandler.behaviour = { throw JobFailed("provider_busy", retryable = true) }

        jobRunner.run(second)

        assertThat(row(second)).extracting("state", "errorKind").containsExactly("failed", "provider_busy")
    }

    @Test
    fun `a fault that will not pass fails the job at once`() {
        val job = claimed()
        fakeJobHandler.behaviour = { throw JobFailed("cut_at_cap", retryable = false) }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "cut_at_cap")
    }

    @Test
    fun `an unexpected error fails the job`() {
        val job = claimed()
        fakeJobHandler.behaviour = { error("bug") }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "error")
    }

    @Test
    fun `a job asked to stop that then fails ends cancelled`() {
        val job = claimed()
        fakeJobHandler.behaviour = {
            job.requestCancel()
            throw JobFailed("provider_busy", retryable = true)
        }

        jobRunner.run(job)

        assertThat(state(job)).isEqualTo("cancelled")
    }

    @Test
    fun `a job no handler knows fails`() {
        val job = claimed()
        val accountService = Mockito.mock(AccountService::class.java)
        Mockito.`when`(accountService.isActive(account)).thenReturn(true)
        // Every kind has a handler once the application is whole, so this runner is given none.
        val withoutHandlers =
            JobRunner(
                jobHandlers = NoHandlers,
                jdbcJobQueue = jdbcJobQueue,
                transactionTemplate = TransactionTemplate(),
                clock = clock,
                accountService = accountService,
            )

        withoutHandlers.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "no_handler")
    }

    @Test
    fun `a job whose writes fail ends failed, not left running to be run again`() {
        val job = claimed()
        fakeJobHandler.behaviour = { { error("constraint") } }

        jobRunner.run(job)

        assertThat(row(job)).extracting("state", "errorKind").containsExactly("failed", "write_failed")
    }

    @Test
    fun `a cancel the heartbeat has not yet passed on still wins over a success`() {
        val job = claimed()
        var written = false
        fakeJobHandler.behaviour = {
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
        fakeJobHandler.behaviour = {
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
        fakeJobHandler.behaviour = {
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
