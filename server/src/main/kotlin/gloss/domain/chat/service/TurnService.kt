package gloss.domain.chat.service

import gloss.common.error.GlossException
import gloss.common.util.traceOf
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.chat.dto.response.ChatMessageResponse
import gloss.domain.llm.service.DeepSeekModel
import gloss.domain.llm.service.LlmCallError
import gloss.domain.llm.service.LlmService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * One turn of the tutor, from the reader's message to Gloss's reply, streamed as it is written.
 * The call goes through [LlmService], which reserves before it and settles or releases after it on
 * every path; this keeps the reply however the call ended, stopped, cut, failed or the reader gone.
 */
@Service
class TurnService(
    private val chatService: ChatService,
    private val llmService: LlmService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** A failure before the reader's message is kept is thrown; any after it ends the turn as a failed reply. */
    fun ask(
        accountId: UUID,
        bookId: UUID,
        request: ChatAskRequest,
        stream: TurnStream,
    ) {
        val turn = chatService.open(accountId, bookId, request)
        try {
            stream.send(TurnEvent.Asked(ChatMessageResponse.from(turn.asked)))
            val answer = answerOf(turn, stream)
            val reply = chatService.close(turn, answer)
            stream.send(TurnEvent.Answered(reply))
        } finally {
            chatService.end(turn)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun answerOf(
        turn: Turn,
        stream: TurnStream,
    ): Answer {
        // Pieces arrive on the call's thread, while a cancel check may run on another.
        val streamed = StringBuffer()
        return try {
            val reply =
                llmService.stream(
                    turn.call,
                    onText = { piece ->
                        streamed.append(piece)
                        stream.send(TurnEvent.Text(piece))
                    },
                    isCancelled = { turn.stop.get() || stream.isClosed() },
                )
            Answer.of(reply, streamed.toString())
        } catch (e: LlmCallError) {
            val failure = Answer.Failure(usage = e.usage, reservationId = e.reservationId)
            Answer.failed(
                errorCode = e.error.code,
                streamed = streamed.toString(),
                model = DeepSeekModel.ID,
                failure = failure,
            )
        } catch (e: GlossException) {
            Answer.failed(e.error.code, streamed.toString(), DeepSeekModel.ID)
        } catch (e: RuntimeException) {
            // As a settlement the database refused: the reader still learns the turn has ended.
            log.warn("A tutor turn failed{}", traceOf(e))
            Answer.failed(ERROR, streamed.toString(), DeepSeekModel.ID)
        }
    }

    private companion object {
        const val ERROR = "error"
    }
}
