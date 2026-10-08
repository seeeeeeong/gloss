package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class GenerationJobSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private data class Job(
        val key: String = "post:${UUID.randomUUID()}",
        val state: String = "queued",
        val attempt: Int = 0,
        val leased: Boolean = false,
        val finished: Boolean = false,
        val errorKind: String? = null,
        val payload: String = "{}",
        val kind: String = "post",
        val account: UUID = UUID.randomUUID(),
    )

    private fun insert(job: Job) =
        jdbc
            .sql(
                """
                insert into generation_job (id, account_id, kind, dedupe_key, payload, state, attempt, run_after,
                                            lease_owner, lease_expires_at, error_kind, created_at, finished_at)
                values (:id, :account, :kind, :key, cast(:payload as jsonb), :state, :attempt, now(),
                        :owner, case when :leased then now() end, :error, now(), case when :finished then now() end)
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("account", job.account)
            .param("kind", job.kind)
            .param("key", job.key)
            .param("payload", job.payload)
            .param("state", job.state)
            .param("attempt", job.attempt)
            .param("owner", if (job.leased) "worker" else null)
            .param("leased", job.leased)
            .param("error", job.errorKind)
            .param("finished", job.finished)
            .update()

    @Test
    fun `one live job per key for a reader`() {
        val account = UUID.randomUUID()
        insert(Job(key = "plan:toc", account = account))

        assertThatThrownBy {
            insert(Job(key = "plan:toc", state = "running", attempt = 1, leased = true, account = account))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `another reader's job for the same key is its own`() {
        insert(Job(key = "plan:toc"))

        assertThatCode { insert(Job(key = "plan:toc")) }.doesNotThrowAnyException()
    }

    @Test
    fun `a finished job leaves its key free`() {
        val account = UUID.randomUUID()
        insert(Job(key = "plan:toc", state = "succeeded", attempt = 1, finished = true, account = account))
        insert(
            Job(
                key = "plan:toc",
                state = "failed",
                attempt = 2,
                finished = true,
                errorKind = "error",
                account = account,
            ),
        )

        assertThatCode { insert(Job(key = "plan:toc", account = account)) }.doesNotThrowAnyException()
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a running job holds a lease`() {
        assertThatThrownBy { insert(Job(state = "running", attempt = 1)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a waiting job holds none`() {
        assertThatThrownBy { insert(Job(leased = true)) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a finished job says when`() {
        assertThatThrownBy { insert(Job(state = "succeeded", attempt = 1)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a waiting job has not finished`() {
        assertThatThrownBy { insert(Job(finished = true)) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a failed job says why`() {
        assertThatThrownBy { insert(Job(state = "failed", attempt = 1, finished = true)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a job runs no more than its attempts allow`() {
        assertThatThrownBy { insert(Job(state = "running", attempt = 3, leased = true)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a payload is an object`() {
        assertThatThrownBy { insert(Job(payload = "[]")) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a kind is one the worker knows`() {
        assertThatThrownBy { insert(Job(kind = "chat")) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
