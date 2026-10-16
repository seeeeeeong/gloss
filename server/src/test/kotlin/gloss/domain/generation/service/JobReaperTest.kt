package gloss.domain.generation.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.infrastructure.JdbcJobQueue
import gloss.domain.generation.infrastructure.JdbcJobReap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
class JobReaperTest(
    @Autowired private val jobReaper: JobReaper,
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val jdbcJobReap: JdbcJobReap,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val clock: MutableClock,
) {
    @BeforeEach
    @AfterEach
    fun reset() {
        jdbc.sql("delete from generation_job").update()
        clock.reset()
    }

    private fun claimedJob(): UUID {
        val id =
            jdbcJobQueue.enqueue(
                NewJob(
                    accountId = UUID.randomUUID(),
                    kind = JobKind.POST,
                    dedupeKey = "post:${UUID.randomUUID()}",
                    payload = mapOf(),
                ),
                clock.instant(),
            )
        jdbcJobQueue.claim("worker-a", clock.instant(), Duration.ofSeconds(60))
        return id
    }

    private fun leasesRunOut() {
        jdbc
            .sql(
                "update generation_job set lease_expires_at = now() - interval '1 second' where state = 'running'",
            ).update()
    }

    private fun state(id: UUID): String =
        jdbc
            .sql("select state from generation_job where id = :id")
            .param("id", id)
            .query(String::class.java)
            .single()

    @Test
    fun `a job whose worker stopped renewing its lease is handed on`() {
        val id = claimedJob()

        assertThat(jobReaper.reap()).isZero()
        leasesRunOut()

        assertThat(jobReaper.reap()).isEqualTo(1)
        assertThat(state(id)).isEqualTo("queued")
    }

    @Test
    fun `only one instance reaps at a time`() {
        val id = claimedJob()
        leasesRunOut()

        val reapedMeanwhile =
            transactionTemplate.execute {
                assertThat(jdbcJobReap.tryLockReap()).isTrue()
                CompletableFuture.supplyAsync { jobReaper.reap() }.join()
            }

        assertThat(reapedMeanwhile).isZero()
        assertThat(state(id)).isEqualTo("running")
    }
}
