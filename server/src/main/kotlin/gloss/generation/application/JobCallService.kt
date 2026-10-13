package gloss.generation.application

import gloss.common.error.GlossException
import gloss.common.util.traceOf
import gloss.generation.domain.CallTokens
import gloss.generation.domain.GenerationCall
import gloss.generation.infrastructure.JdbcGenerationCalls
import gloss.llm.application.DeepSeekModel
import gloss.llm.application.LlmCall
import gloss.llm.application.LlmCallError
import gloss.llm.application.LlmReply
import gloss.llm.application.LlmService
import gloss.llm.application.ReplyUsage
import gloss.llm.domain.chat.FinishReason
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * A job's model call, recorded however it ends. Only a complete answer comes back: anything else
 * is a [JobFailed] that names why, to be tried again when the fault may pass by itself.
 */
@Service
class JobCallService(
    private val llmService: LlmService,
    private val jdbcGenerationCalls: JdbcGenerationCalls,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [promptVersion] names the prompt the call was built from, so a change in the cache's hits has a cause. */
    fun complete(
        job: RunningJob,
        call: LlmCall,
        promptVersion: String,
    ): LlmReply {
        val asked = Asked(job, call, promptVersion)
        val reply = answerTo(asked)
        record(asked, errorCode = CANCELLED.takeIf { reply.cancelled }, reply = reply)
        throw failureOf(reply) ?: return reply
    }

    @Suppress("TooGenericExceptionCaught")
    private fun answerTo(asked: Asked): LlmReply =
        try {
            llmService.stream(asked.call, onText = {}, isCancelled = asked.job::isCancelled)
        } catch (e: GlossException) {
            record(asked, errorCode = e.error.code, failure = e as? LlmCallError)
            throw JobFailed(e.error.code, retryable = e.error.status in PASSING, cause = e)
        } catch (e: RuntimeException) {
            // Anything else, as a settlement the database refused: the call may have gone out and billed.
            record(asked, errorCode = ERROR)
            throw e
        }

    private fun failureOf(reply: LlmReply): JobFailed? {
        // Cut short by the provider's own load, not by the answer: another attempt may finish.
        val passing = reply.finishReason == FinishReason.INSUFFICIENT_SYSTEM_RESOURCE
        return when {
            reply.cancelled -> JobFailed(CANCELLED, retryable = false)
            reply.finishReason == FinishReason.STOP -> null
            else -> JobFailed("$UNFINISHED${reply.finishReason.code}", retryable = passing)
        }
    }

    private class Asked(
        val job: RunningJob,
        val call: LlmCall,
        val promptVersion: String,
    )

    // A side record of money already spent: failing to write it never takes back the answer.
    @Suppress("TooGenericExceptionCaught")
    private fun record(
        asked: Asked,
        errorCode: String? = null,
        reply: LlmReply? = null,
        failure: LlmCallError? = null,
    ) {
        val call =
            GenerationCall(
                lease = asked.job.claimed.lease,
                accountId = asked.call.accountId,
                purpose = asked.call.purpose.code,
                model = reply?.model ?: DeepSeekModel.ID,
                promptVersion = asked.promptVersion,
                finishReason = reply?.finishReason?.code.takeIf { errorCode == null },
                errorCode = errorCode,
                tokens = (reply?.usage ?: failure?.usage)?.let(::tokensOf),
                reservationId = reply?.reservationId ?: failure?.reservationId,
                occurredAt = clock.instant(),
            )
        try {
            jdbcGenerationCalls.append(call)
        } catch (e: RuntimeException) {
            log.warn("Could not record a call of job {}{}", call.lease.jobId, traceOf(e))
        }
    }

    private fun tokensOf(usage: ReplyUsage) =
        CallTokens(
            cacheHit = usage.cacheHitTokens,
            cacheMiss = usage.cacheMissTokens,
            completion = usage.completionTokens,
            reasoning = usage.reasoningTokens,
        )

    private companion object {
        const val CANCELLED = "cancelled"
        const val ERROR = "error"
        const val UNFINISHED = "finish."

        // The provider busy or failing, or the reader's other calls in the way: gone in a while.
        val PASSING = setOf(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)
    }
}
