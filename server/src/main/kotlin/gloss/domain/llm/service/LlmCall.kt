package gloss.domain.llm.service

import gloss.common.error.GlossException
import gloss.domain.budget.service.Purpose
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Thinking
import java.util.UUID

/** [maxTokens] and [thinking] have no defaults: each caller decides, and pays for, its own. */
data class LlmCall(
    val accountId: UUID,
    val purpose: Purpose,
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val thinking: Thinking,
    val format: ReplyFormat = ReplyFormat.TEXT,
)

/**
 * Only a reply that finished with [FinishReason.STOP] is complete; the caller checks before storing it.
 * [usage] is null when the provider never reported it, and [reservationId] when nothing was reserved.
 */
data class LlmReply(
    val text: String,
    val finishReason: FinishReason,
    val cancelled: Boolean = false,
    val model: String = DeepSeekModel.ID,
    val usage: ReplyUsage? = null,
    val reservationId: UUID? = null,
) {
    companion object {
        internal fun of(
            result: ChatResult,
            reservationId: UUID,
        ) = LlmReply(
            text = result.text,
            finishReason = result.finishReason,
            usage = result.usage?.let(ReplyUsage::of),
            reservationId = reservationId,
        )
    }
}

/** As the provider reported it: how much of the prompt its cache held, and what the answer took. */
data class ReplyUsage(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val reasoningTokens: Long?,
) {
    companion object {
        internal fun of(usage: TokenUsage) =
            ReplyUsage(
                cacheHitTokens = usage.cacheHitTokens,
                cacheMissTokens = usage.cacheMissTokens,
                completionTokens = usage.completionTokens,
                reasoningTokens = usage.reasoningTokens,
            )
    }
}

/**
 * A call that failed once it was reserved: the reservation it was ended under, and what the
 * provider reported the call used, when it did before failing.
 */
class LlmCallError(
    error: LlmError,
    val reservationId: UUID,
    val usage: ReplyUsage?,
    cause: Throwable?,
) : GlossException(error, cause) {
    companion object {
        private const val serialVersionUID = 1L

        internal fun of(
            failure: LlmCallFailed,
            reservationId: UUID,
        ) = LlmCallError(
            error = LlmError.of(failure.kind),
            reservationId = reservationId,
            usage = failure.usage?.let(ReplyUsage::of),
            cause = failure,
        )
    }
}
