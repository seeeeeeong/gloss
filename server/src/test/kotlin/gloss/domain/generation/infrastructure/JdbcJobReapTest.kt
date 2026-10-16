package gloss.domain.generation.infrastructure

import gloss.TestcontainersConfiguration
import gloss.domain.generation.domain.JobEnding
import gloss.domain.generation.domain.Lease
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
class JdbcJobReapTest(
    @Autowired private val jdbcJobQueue: JdbcJobQueue,
    @Autowired private val jdbcJobReap: JdbcJobReap,
    @Autowired private val jdbc: JdbcClient,
) {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val lease = Duration.ofSeconds(60)
    private val account = UUID.randomUUID()

    private data class Row(
        val state: String,
        val attempt: Int,
        val errorKind: String?,
        val runAfter: Instant,
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

    private fun row(id: UUID): Row =
        jdbc
            .sql(
                """
                select state, attempt, error_kind, run_after, finished_at is not null as finished, cancel_requested
                from generation_job where id = :id
                """.trimIndent(),
            ).param("id", id)
            .query { rs, _ ->
                Row(
                    state = rs.getString("state"),
                    attempt = rs.getInt("attempt"),
                    errorKind = rs.getString("error_kind"),
                    runAfter = rs.getTimestamp("run_after").toInstant(),
                    finished = rs.getBoolean("finished"),
                    cancelRequested = rs.getBoolean("cancel_requested"),
                )
            }.single()

    // Leases run on the database's clock, so a test ends one there rather than moving a clock.
    private fun leasesRunOut() {
        jdbc
            .sql(
                "update generation_job set lease_expires_at = now() - interval '1 second' where state = 'running'",
            ).update()
    }

    @Test
    fun `a job whose lease ran out goes back to the queue, or fails once its attempts are used`() {
        val once = jdbcJobQueue.enqueue(job(), now)
        checkNotNull(claim())

        assertThat(jdbcJobReap.reap(now, limit = 10)).isZero()
        leasesRunOut()
        assertThat(jdbcJobReap.reap(now, limit = 10)).isEqualTo(1)
        assertThat(row(once).state).isEqualTo("queued")
        assertThat(row(once).errorKind).isEqualTo("lease_lost")

        checkNotNull(claim())
        leasesRunOut()
        jdbcJobReap.reap(now, limit = 10)

        assertThat(row(once).state).isEqualTo("failed")
        assertThat(row(once).finished).isTrue()
    }

    @Test
    fun `a job asked to stop whose lease ran out ends cancelled`() {
        val id = jdbcJobQueue.enqueue(job(), now)
        checkNotNull(claim())
        jdbcJobQueue.cancelAll(account, now)
        leasesRunOut()

        jdbcJobReap.reap(now, limit = 10)

        assertThat(row(id).state).isEqualTo("cancelled")
    }
}
