package gloss.domain.generation.infrastructure

import gloss.domain.generation.domain.GenerationCall
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

/** The model calls jobs made. Append-only: a call that was made stays made. */
@Repository
class JdbcGenerationCalls(
    private val jdbcClient: JdbcClient,
) {
    fun append(call: GenerationCall) {
        jdbcClient
            .sql(
                """
                insert into generation_call (job_id, attempt, account_id, purpose, model, prompt_version,
                                             finish_reason, error_code, cache_hit_tokens, cache_miss_tokens,
                                             completion_tokens, reasoning_tokens, reservation_id, occurred_at)
                values (:job, :attempt, :account, :purpose, :model, :promptVersion, :finishReason, :errorCode,
                        :cacheHit, :cacheMiss, :completion, :reasoning, :reservation, :occurredAt)
                """.trimIndent(),
            ).param("job", call.lease.jobId)
            .param("attempt", call.lease.attempt)
            .param("account", call.accountId)
            .param("purpose", call.purpose)
            .param("model", call.model)
            .param("promptVersion", call.promptVersion)
            .param("finishReason", call.finishReason)
            .param("errorCode", call.errorCode)
            .param("cacheHit", call.tokens?.cacheHit)
            .param("cacheMiss", call.tokens?.cacheMiss)
            .param("completion", call.tokens?.completion)
            .param("reasoning", call.tokens?.reasoning)
            .param("reservation", call.reservationId)
            .param("occurredAt", Timestamp.from(call.occurredAt))
            .update()
    }
}
