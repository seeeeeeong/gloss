package gloss.domain.generation.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.account.Members
import gloss.domain.account.service.AccountService
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.JobState
import gloss.domain.generation.domain.job.JobStatus
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.newReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class GenerationServiceTest(
    @Autowired private val generationService: GenerationService,
    @Autowired private val accountService: AccountService,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)
    private val account = members.newReader(jdbc)

    @AfterEach
    fun cleanUp() {
        jdbc.sql("delete from generation_job where account_id = :account").param("account", account).update()
    }

    private fun job(key: String) =
        NewJob(
            accountId = account,
            kind = JobKind.PLAN,
            dedupeKey = key,
            payload =
                mapOf("toc" to "t-1"),
        )

    private fun states(): List<String?> =
        jdbc
            .sql("select state from generation_job where account_id = :account")
            .param("account", account)
            .query(String::class.java)
            .list()

    @Test
    fun `a job exists only if the work that asked for it commits`() {
        transactionTemplate.executeWithoutResult {
            generationService.enqueue(job("plan:${UUID.randomUUID()}"))
            it.setRollbackOnly()
        }

        assertThat(states()).isEmpty()
    }

    @Test
    fun `asking twice for the same work gets one job`() {
        val key = "plan:${UUID.randomUUID()}"

        val first = generationService.enqueue(job(key))
        val second = generationService.enqueue(job(key))

        assertThat(second).isEqualTo(first)
        assertThat(states()).containsExactly("queued")
    }

    @Test
    fun `the newest job for a piece of work says how it stands, and work never asked for has none`() {
        val key = "plan:${UUID.randomUUID()}"
        assertThat(generationService.latest(account, key)).isNull()

        generationService.enqueue(job(key))
        assertThat(generationService.latest(account, key)).isEqualTo(JobStatus(JobState.QUEUED, null))

        jdbc
            .sql(
                """
                update generation_job set state = 'failed', error_kind = 'llm.no_key', finished_at = now()
                where account_id = :account and dedupe_key = :key
                """.trimIndent(),
            ).param("account", account)
            .param("key", key)
            .update()
        assertThat(generationService.latest(account, key)).isEqualTo(JobStatus(JobState.FAILED, "llm.no_key"))
        assertThat(generationService.latest(UUID.randomUUID(), key)).isNull()

        generationService.enqueue(job(key))
        assertThat(generationService.latest(account, key)?.state).isEqualTo(JobState.QUEUED)
    }

    private fun disable() {
        jdbc
            .sql(
                "update account set status = 'disabled', disabled_at = now() where id = :id",
            ).param("id", account)
            .update()
    }

    @Test
    fun `a disabled account's waiting jobs are cancelled`() {
        generationService.enqueue(job("plan:${UUID.randomUUID()}"))
        disable()

        generationService.cancelForDisabledAccount(account)

        assertThat(states()).containsExactly("cancelled")
    }

    @Test
    fun `a disabled reader is given no job`() {
        jdbc
            .sql(
                "update account set status = 'disabled', disabled_at = now() where id = :id",
            ).param("id", account)
            .update()

        assertThatThrownBy { generationService.enqueue(job("plan:${UUID.randomUUID()}")) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(GenerationError.ACCOUNT_DISABLED)
            }
        assertThat(states()).isEmpty()
    }

    @Test
    fun `a job still being added when its reader is disabled is cancelled once it commits`() {
        val added = CountDownLatch(1)
        val commit = CountDownLatch(1)
        // Threads of their own: the common pool may have one thread, which the first would hold.
        val threads = Executors.newVirtualThreadPerTaskExecutor()
        val adding =
            CompletableFuture.runAsync({
                transactionTemplate.executeWithoutResult {
                    generationService.enqueue(job("plan:${UUID.randomUUID()}"))
                    added.countDown()
                    commit.await(10, TimeUnit.SECONDS)
                }
            }, threads)
        assertThat(added.await(10, TimeUnit.SECONDS)).isTrue()

        disable()
        val cancelling = CompletableFuture.runAsync({ generationService.cancelForDisabledAccount(account) }, threads)
        // The cancel must either be done, having missed the uncommitted job, or be waiting for it.
        await().atMost(Duration.ofSeconds(10)).until { cancelling.isDone || waitingOnALock() }
        commit.countDown()
        adding.join()
        cancelling.join()

        assertThat(states()).containsExactly("cancelled")
    }

    private fun waitingOnALock(): Boolean =
        jdbc
            .sql(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()",
            ).query(Long::class.java)
            .single() > 0

    @Test
    fun `a late disable for a reader enabled again cancels nothing`() {
        generationService.enqueue(job("plan:${UUID.randomUUID()}"))

        generationService.cancelForDisabledAccount(account)

        assertThat(states()).containsExactly("queued")
    }

    @Test
    fun `a reader disabled after the caller read them is still refused`() {
        assertThatThrownBy {
            transactionTemplate.executeWithoutResult {
                accountService.getMyAccount(account)
                // Committed apart from the caller's transaction, as an operator's disable is.
                CompletableFuture.runAsync({ disable() }, Executors.newVirtualThreadPerTaskExecutor()).join()
                generationService.enqueue(job("plan:${UUID.randomUUID()}"))
            }
        }.isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(GenerationError.ACCOUNT_DISABLED)
        }
    }
}
