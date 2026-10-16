package gloss.domain.chat.service

import gloss.domain.chat.domain.ChatLine
import gloss.domain.chat.domain.ChatSession
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.domain.ChatUsage
import gloss.domain.chat.dto.response.ChatMessageResponse
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.llm.service.ReplyUsage
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** A reader's message, kept, and the call that answers it; [stop] is set when the reader stops it. */
class Turn(
    val session: ChatSession,
    val turnId: UUID,
    val asked: ChatLine,
    val call: LlmCall,
    val stop: AtomicBoolean,
)

/** What a turn sends the reader, in this order: their message as kept, the answer's pieces, the reply as kept. */
sealed interface TurnEvent {
    data class Asked(
        val message: ChatMessageResponse,
    ) : TurnEvent

    data class Text(
        val text: String,
    ) : TurnEvent

    data class Answered(
        val message: ChatMessageResponse,
    ) : TurnEvent
}

/** Where a turn's events go. [send] hands an event off and returns; [isClosed] is true once the reader has gone. */
class TurnStream(
    val send: (TurnEvent) -> Unit,
    val isClosed: () -> Boolean,
)

/** How the call ended, to be kept as Gloss's reply. [text] is what the reader was shown of it. */
data class Answer(
    val status: ChatStatus,
    val text: String,
    val finishReason: String? = null,
    val errorCode: String? = null,
    val model: String,
    val usage: ChatUsage? = null,
    val reservationId: UUID? = null,
) {
    companion object {
        fun of(
            reply: LlmReply,
            streamed: String,
        ): Answer {
            // Finished with nothing to read: billed as it was, but never an answer to show or send back.
            if (!reply.cancelled && reply.finishReason == FinishReason.STOP && reply.text.isBlank()) {
                val failure = reply.reservationId?.let { Failure(usage = reply.usage, reservationId = it) }
                return failed(
                    errorCode = ChatError.EMPTY_ANSWER.code,
                    streamed = "",
                    model = reply.model,
                    failure = failure,
                )
            }
            val status =
                when {
                    reply.cancelled -> ChatStatus.STOPPED
                    reply.finishReason == FinishReason.STOP -> ChatStatus.DONE
                    else -> ChatStatus.CUT
                }
            return Answer(
                status = status,
                // A stopped call returns no text of its own: the reader saw what had come.
                text = if (reply.cancelled) streamed else reply.text,
                finishReason = reply.finishReason.code.takeIf { !reply.cancelled },
                model = reply.model,
                usage = reply.usage?.let(::usageOf),
                reservationId = reply.reservationId,
            )
        }

        fun failed(
            errorCode: String,
            streamed: String,
            model: String,
            failure: Failure? = null,
        ) = Answer(
            status = ChatStatus.FAILED,
            text = streamed,
            errorCode = errorCode,
            model = model,
            usage = failure?.usage?.let(::usageOf),
            reservationId = failure?.reservationId,
        )

        private fun usageOf(usage: ReplyUsage) =
            ChatUsage(
                cacheHitTokens = usage.cacheHitTokens,
                cacheMissTokens = usage.cacheMissTokens,
                completionTokens = usage.completionTokens,
                reasoningTokens = usage.reasoningTokens,
            )
    }

    /** What a call that failed once reserved left: what the provider said it used, and the reservation. */
    data class Failure(
        val usage: ReplyUsage?,
        val reservationId: UUID,
    )
}
