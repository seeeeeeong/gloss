package gloss.generation.application

import gloss.TestcontainersConfiguration
import gloss.account.Members
import gloss.generation.FakePlanHandler
import gloss.generation.domain.job.JobKind
import gloss.generation.domain.job.NewJob
import gloss.generation.newReader
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// A fast heartbeat, so a test sees a cancel or a renewed lease in well under a second.
@SpringBootTest(properties = ["gloss.generation.heartbeat-every=PT0.2S"])
@Import(TestcontainersConfiguration::class, FakePlanHandler.Config::class)
class JobWorkerTest(
    @Autowired private val jobWorker: JobWorker,
    @Autowired private val generationService: GenerationService,
    @Autowired private val fakePlanHandler: FakePlanHandler,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)
    private val account = members.newReader(jdbc)

    private companion object {
        // Each step takes milliseconds; the margin is for a loaded CI runner.
        val WAIT: Duration = Duration.ofSeconds(10)
    }

    @BeforeEach
    fun startWorker() {
        jdbc.sql("delete from generation_job").update()
        jobWorker.start()
    }

    @AfterEach
    fun stopWorker() {
        val stopped = CountDownLatch(1)
        jobWorker.stop { stopped.countDown() }
        stopped.await(WAIT.toSeconds(), TimeUnit.SECONDS)
        fakePlanHandler.behaviour = { {} }
        jdbc.sql("delete from generation_job").update()
    }

    // Enqueued in a transaction of its own, so the worker is woken when it commits.
    private fun enqueue(): UUID =
        generationService.enqueue(
            NewJob(
                accountId = account,
                kind = JobKind.PLAN,
                dedupeKey = "plan:${UUID.randomUUID()}",
                payload = mapOf(),
            ),
        )

    private fun state(id: UUID): String =
        jdbc
            .sql("select state from generation_job where id = :id")
            .param("id", id)
            .query(String::class.java)
            .single()

    private fun leaseExpiry(id: UUID): Instant =
        jdbc
            .sql("select lease_expires_at from generation_job where id = :id")
            .param("id", id)
            .query { rs, _ -> rs.getTimestamp(1).toInstant() }
            .single()

    @Test
    fun `a job is run once its transaction commits, without waiting for the next poll`() {
        val id = enqueue()

        // The poll is five seconds; only the wake-up after commit gets there sooner.
        await().atMost(Duration.ofSeconds(3)).until { state(id) == "succeeded" }
    }

    @Test
    fun `a running job hears of a cancel at its next heartbeat`() {
        fakePlanHandler.behaviour = { job ->
            await().atMost(WAIT).until { job.isCancelled() }
            val write: () -> Unit = {}
            write
        }
        val id = enqueue()
        await().atMost(WAIT).until { state(id) == "running" }

        jdbc
            .sql(
                "update account set status = 'disabled', disabled_at = now() where id = :id",
            ).param("id", account)
            .update()
        generationService.cancelForDisabledAccount(account)

        await().atMost(WAIT).until { state(id) == "cancelled" }
    }

    @Test
    fun `a running job's lease is renewed until it ends`() {
        val release = CountDownLatch(1)
        fakePlanHandler.behaviour = {
            release.await(WAIT.toSeconds(), TimeUnit.SECONDS)
            val write: () -> Unit = {}
            write
        }
        val id = enqueue()
        await().atMost(WAIT).until { state(id) == "running" }
        val first = leaseExpiry(id)

        await().atMost(WAIT).until { leaseExpiry(id) > first }
        release.countDown()

        await().atMost(WAIT).until { state(id) == "succeeded" }
    }

    @Test
    fun `stopping waits for the running job, which still ends well`() {
        val release = CountDownLatch(1)
        fakePlanHandler.behaviour = {
            release.await(WAIT.toSeconds(), TimeUnit.SECONDS)
            val write: () -> Unit = {}
            write
        }
        val id = enqueue()
        await().atMost(WAIT).until { state(id) == "running" }
        val stopped = CountDownLatch(1)

        jobWorker.stop { stopped.countDown() }

        assertThat(stopped.count).isEqualTo(1)
        release.countDown()
        assertThat(stopped.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue()
        assertThat(state(id)).isEqualTo("succeeded")
    }
}
