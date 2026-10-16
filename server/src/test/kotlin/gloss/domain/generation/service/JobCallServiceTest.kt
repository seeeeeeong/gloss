package gloss.domain.generation.service

import gloss.TestcontainersConfiguration
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.apikey.service.ApiKeyService
import gloss.domain.budget.service.BudgetService
import gloss.domain.budget.service.Purpose
import gloss.domain.generation.domain.ClaimedJob
import gloss.domain.generation.domain.GenerationCall
import gloss.domain.generation.domain.Lease
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.infrastructure.JdbcGenerationCalls
import gloss.domain.llm.FakeLlmGateway
import gloss.domain.llm.KeyedReaders
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.service.LlmCall
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Instant
import java.util.UUID

// Not transactional: a model call commits its reservation and settlement, and its record, on its own.
@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeKeyCheckGateway.Config::class, FakeLlmGateway.Config::class)
@MockitoSpyBean(types = [JdbcGenerationCalls::class])
class JobCallServiceTest(
    @Autowired private val jobCallService: JobCallService,
    @Autowired private val fakeLlmGateway: FakeLlmGateway,
    @Autowired private val jdbcGenerationCalls: JdbcGenerationCalls,
    @Autowired private val jdbc: JdbcClient,
    @Autowired apiKeyService: ApiKeyService,
    @Autowired budgetService: BudgetService,
) {
    private val readers = KeyedReaders(jdbc, apiKeyService, budgetService)
    private val usage = TokenUsage(cacheHitTokens = 20, cacheMissTokens = 10, completionTokens = 7, reasoningTokens = 0)

    private data class Row(
        val attempt: Int,
        val purpose: String,
        val model: String,
        val promptVersion: String,
        val finishReason: String?,
        val errorCode: String?,
        val tokens: List<Long?>,
        val reservationId: UUID?,
    )

    @BeforeEach
    fun reset() {
        fakeLlmGateway.reset()
    }

    @AfterEach
    fun clear() {
        readers.removeAll()
    }

    private fun job(account: UUID) =
        RunningJob(
            ClaimedJob(
                lease = Lease(jobId = UUID.randomUUID(), attempt = 2, owner = "worker-a"),
                accountId = account,
                kind = JobKind.PLAN,
                maxAttempts = 2,
                payload = mapOf(),
            ),
        )

    private fun call(account: UUID) =
        LlmCall(
            accountId = account,
            purpose = Purpose.PLAN,
            messages = listOf(ChatMessage(Role.SYSTEM, "rules"), ChatMessage(Role.USER, "contents")),
            maxTokens = 100,
            thinking = Thinking.DISABLED,
        )

    private fun rows(job: RunningJob): List<Row> =
        jdbc
            .sql(
                """
                select attempt, purpose, model, prompt_version, finish_reason, error_code, cache_hit_tokens,
                       cache_miss_tokens, completion_tokens, reasoning_tokens, reservation_id
                from generation_call where job_id = :job order by id
                """.trimIndent(),
            ).param("job", job.claimed.lease.jobId)
            .query { rs, _ ->
                Row(
                    attempt = rs.getInt(1),
                    purpose = rs.getString(2),
                    model = rs.getString(3),
                    promptVersion = rs.getString(4),
                    finishReason = rs.getString(5),
                    errorCode = rs.getString(6),
                    tokens = (7..10).map { rs.getObject(it) as Long? },
                    reservationId = rs.getObject(11) as UUID?,
                )
            }.list()

    private fun failure(call: () -> Unit): JobFailed {
        var failed: JobFailed? = null
        assertThatThrownBy(call).isInstanceOfSatisfying(JobFailed::class.java) { failed = it }
        return checkNotNull(failed)
    }

    @Test
    fun `a complete answer comes back, recorded with what it used and the reservation it settled`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ -> ChatResult("{}", FinishReason.STOP, usage) }

        val reply = jobCallService.complete(job, call(account), "plan-1")

        assertThat(reply.text).isEqualTo("{}")
        assertThat(rows(job)).containsExactly(
            Row(
                attempt = 2,
                purpose = "plan",
                model = "deepseek-flash",
                promptVersion = "plan-1",
                finishReason = "stop",
                errorCode = null,
                tokens = listOf(20, 10, 7, 0),
                reservationId = reply.reservationId,
            ),
        )
        assertThat(reply.reservationId).isNotNull()
    }

    @Test
    fun `a call refused before it goes out fails the job with the reason, for good`() {
        val account = readers.create(key = false)
        val job = job(account)

        val failed = failure { jobCallService.complete(job, call(account), "plan-1") }

        assertThat(failed.errorKind).isEqualTo("llm.no_key")
        assertThat(failed.retryable).isFalse()
        assertThat(rows(job).single().errorCode).isEqualTo("llm.no_key")
        assertThat(rows(job).single().finishReason).isNull()
        assertThat(rows(job).single().tokens).containsOnlyNulls()
    }

    @Test
    fun `a call that failed after the provider said what it used is recorded with that and its reservation`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ ->
            throw LlmCallFailed(LlmCallFailed.Kind.NETWORK, outputStarted = true, usage = usage)
        }

        failure { jobCallService.complete(job, call(account), "plan-1") }

        val row = rows(job).single()
        assertThat(row.errorCode).isEqualTo("llm.provider_failed")
        assertThat(row.tokens).containsExactly(20L, 10L, 7L, 0L)
        assertThat(row.reservationId).isNotNull()
    }

    @Test
    fun `a provider that is busy fails the job for now`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ -> throw LlmCallFailed(LlmCallFailed.Kind.RATE_LIMITED, outputStarted = false) }

        val failed = failure { jobCallService.complete(job, call(account), "plan-1") }

        assertThat(failed.errorKind).isEqualTo("llm.provider_busy")
        assertThat(failed.retryable).isTrue()
    }

    @Test
    fun `a request the provider refuses as it stands fails the job for good, and one it failed at may pass`() {
        val account = readers.create()
        fakeLlmGateway.answer =
            { _, _ -> throw LlmCallFailed(LlmCallFailed.Kind.REFUSED_REQUEST, outputStarted = false) }
        val refused = failure { jobCallService.complete(job(account), call(account), "plan-1") }
        fakeLlmGateway.answer =
            { _, _ -> throw LlmCallFailed(LlmCallFailed.Kind.PROVIDER_ERROR, outputStarted = false) }
        val failed = failure { jobCallService.complete(job(account), call(account), "plan-1") }

        assertThat(refused.errorKind).isEqualTo("llm.request_refused")
        assertThat(refused.retryable).isFalse()
        assertThat(failed.errorKind).isEqualTo("llm.provider_failed")
        assertThat(failed.retryable).isTrue()
    }

    @Test
    fun `an answer cut at its cap is recorded with what it used, and is no answer`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ -> ChatResult("{\"items\": [", FinishReason.LENGTH, usage) }

        val failed = failure { jobCallService.complete(job, call(account), "plan-1") }

        assertThat(failed.errorKind).isEqualTo("finish.length")
        assertThat(failed.retryable).isFalse()
        assertThat(rows(job).single().finishReason).isEqualTo("length")
        assertThat(rows(job).single().tokens).containsExactly(20L, 10L, 7L, 0L)
    }

    @Test
    fun `an answer that ended for a reason of its own comes back to a caller that judges it, recorded`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ -> ChatResult("{\"items\": [", FinishReason.LENGTH, usage) }

        val reply = jobCallService.answer(job, call(account), "plan-1")

        assertThat(reply.finishReason).isEqualTo(FinishReason.LENGTH)
        assertThat(reply.text).isEqualTo("{\"items\": [")
        assertThat(rows(job).single().finishReason).isEqualTo("length")
        assertThat(rows(job).single().reservationId).isEqualTo(reply.reservationId)
    }

    @Test
    fun `an answer cut for the provider's own load, or a job cancelled, is not handed to a judge`() {
        val account = readers.create()
        fakeLlmGateway.answer = { _, _ -> ChatResult("{", FinishReason.INSUFFICIENT_SYSTEM_RESOURCE, usage) }

        val busy = failure { jobCallService.answer(job(account), call(account), "plan-1") }

        assertThat(busy.errorKind).isEqualTo("finish.insufficient_system_resource")
        assertThat(busy.retryable).isTrue()
        val cancelled = job(account).also { it.requestCancel() }
        assertThat(failure { jobCallService.answer(cancelled, call(account), "plan-1") }.errorKind)
            .isEqualTo("cancelled")
    }

    @Test
    fun `an answer the provider cut for its own load may be asked for again`() {
        val account = readers.create()
        fakeLlmGateway.answer = { _, _ -> ChatResult("{", FinishReason.INSUFFICIENT_SYSTEM_RESOURCE, usage) }

        val failed = failure { jobCallService.complete(job(account), call(account), "plan-1") }

        assertThat(failed.errorKind).isEqualTo("finish.insufficient_system_resource")
        assertThat(failed.retryable).isTrue()
    }

    @Test
    fun `a call for a job cancelled meanwhile is recorded as cancelled`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ ->
            job.requestCancel()
            throw LlmCallFailed(LlmCallFailed.Kind.CANCELLED, outputStarted = false, promptMayHaveBilled = true)
        }

        val failed = failure { jobCallService.complete(job, call(account), "plan-1") }

        assertThat(failed.errorKind).isEqualTo("cancelled")
        assertThat(rows(job).single().errorCode).isEqualTo("cancelled")
        assertThat(rows(job).single().reservationId).isNotNull()
    }

    @Test
    fun `a call that ends in a failure of its own is recorded too, and the failure comes back`() {
        val account = readers.create()
        val job = job(account)
        fakeLlmGateway.answer = { _, _ -> error("database blinked") }

        assertThatThrownBy { jobCallService.complete(job, call(account), "plan-1") }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(rows(job).single().errorCode).isEqualTo("error")
        assertThat(rows(job).single().finishReason).isNull()
    }

    @Test
    fun `a record that cannot be written does not take back a complete answer`() {
        val account = readers.create()
        fakeLlmGateway.answer = { _, _ -> ChatResult("{}", FinishReason.STOP, usage) }
        doThrow(IllegalStateException("down")).`when`(jdbcGenerationCalls).append(anyCall())

        val reply = jobCallService.complete(job(account), call(account), "plan-1")

        assertThat(reply.text).isEqualTo("{}")
    }

    // Mockito's matcher returns null, which a Kotlin parameter refuses; the matcher is what counts.
    private fun anyCall(): GenerationCall =
        ArgumentMatchers.any(GenerationCall::class.java)
            ?: GenerationCall(
                lease = Lease(UUID(0, 0), 1, "matcher"),
                accountId = UUID(0, 0),
                purpose = "plan",
                model = "model",
                promptVersion = "prompt",
                finishReason = "stop",
                errorCode = null,
                tokens = null,
                reservationId = null,
                occurredAt = Instant.EPOCH,
            )
}
