package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class GenerationCallSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private data class Call(
        val job: UUID = UUID.randomUUID(),
        val attempt: Int = 1,
        val purpose: String = "plan",
        val finishReason: String? = "stop",
        val errorCode: String? = null,
        val hit: Long? = 20,
        val miss: Long? = 10,
        val completion: Long? = 7,
    )

    private fun insert(call: Call) =
        jdbc
            .sql(
                """
                insert into generation_call (job_id, attempt, account_id, purpose, model, prompt_version,
                                             finish_reason, error_code, cache_hit_tokens, cache_miss_tokens,
                                             completion_tokens, occurred_at)
                values (:job, :attempt, :account, :purpose, 'deepseek-flash', 'plan-1', :finishReason, :errorCode,
                        :hit, :miss, :completion, now())
                """.trimIndent(),
            ).param("job", call.job)
            .param("attempt", call.attempt)
            .param("account", UUID.randomUUID())
            .param("purpose", call.purpose)
            .param("finishReason", call.finishReason)
            .param("errorCode", call.errorCode)
            .param("hit", call.hit)
            .param("miss", call.miss)
            .param("completion", call.completion)
            .update()

    private fun assertRefused(call: Call) {
        assertThatThrownBy { insert(call) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an answer and a call that failed without one are both kept, and a job may make several`() {
        val job = UUID.randomUUID()

        assertThatCode {
            insert(Call(job = job))
            insert(Call(job = job, finishReason = "length"))
            insert(
                Call(
                    job = job,
                    attempt = 2,
                    finishReason = null,
                    errorCode = "llm.no_key",
                    hit = null,
                    miss = null,
                    completion = null,
                ),
            )
        }.doesNotThrowAnyException()
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a call says how it ended`() {
        assertRefused(Call(finishReason = null))
    }

    @Test
    fun `a call ends one way, not two`() {
        assertRefused(Call(errorCode = "cancelled"))
    }

    @Test
    fun `usage is whole or absent`() {
        assertRefused(Call(completion = null))
    }

    @Test
    fun `tokens are never negative`() {
        assertRefused(Call(hit = -1))
    }

    @Test
    fun `a call belongs to an attempt`() {
        assertRefused(Call(attempt = 0))
    }

    @Test
    fun `a tutor's call is not a job's`() {
        assertRefused(Call(purpose = "chat"))
    }

    @Test
    fun `a call is never changed`() {
        val job = UUID.randomUUID()
        insert(Call(job = job))

        assertThatThrownBy {
            jdbc.sql("update generation_call set completion_tokens = 0 where job_id = :job").param("job", job).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }

    @Test
    fun `a call is never deleted`() {
        val job = UUID.randomUUID()
        insert(Call(job = job))

        assertThatThrownBy {
            jdbc.sql("delete from generation_call where job_id = :job").param("job", job).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }
}
