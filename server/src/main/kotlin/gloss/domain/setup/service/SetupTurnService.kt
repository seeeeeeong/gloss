package gloss.domain.setup.service

import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.common.util.traceOf
import gloss.domain.budget.service.Purpose
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.llm.service.LlmService
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.CalledTool
import gloss.domain.setup.domain.GlossSaid
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.SetupTurnPolicy
import gloss.domain.setup.domain.ToolsCalled
import gloss.domain.setup.infrastructure.JdbcSetupMessages
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Answers a reader's message with the model, on the reader's key: each call is reserved and settled by
 * [LlmService], the tools it asks for run between calls, and every call's tools are kept with their
 * answers in one write, so the conversation read back always pairs a call with its answer. No
 * transaction is open across a call or a tool.
 */
@Service
class SetupTurnService(
    private val jdbcSetupMessages: JdbcSetupMessages,
    private val llmService: LlmService,
    private val setupTools: SetupTools,
    private val setupTurnWriter: SetupTurnWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Answers the message the reader sent [since]. However it ends, the session stops waiting: with the
     * answer, or with what went wrong for the reader to read. Nothing is written once a newer message
     * has taken its place.
     */
    @Suppress("TooGenericExceptionCaught")
    fun answer(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
    ) {
        val asked = TurnAsked(accountId, sessionId, since)
        val ending =
            try {
                converse(asked)
            } catch (_: TurnSuperseded) {
                return
            } catch (e: GlossException) {
                failure(e.error)
            } catch (e: RuntimeException) {
                log.warn("A setup turn failed{}", traceOf(e))
                failure(SetupError.TURN_FAILED)
            }
        try {
            if (ending != null) setupTurnWriter.keep(asked, listOf(ending), done = true)
        } catch (_: TurnSuperseded) {
            // A newer message took its place while this one was answered.
        }
    }

    /** Ends the turn at once with [error], as when no turn can be run for it now. */
    fun refuse(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
        error: ErrorCode,
    ) {
        try {
            setupTurnWriter.keep(TurnAsked(accountId, sessionId, since), listOf(failure(error)), done = true)
        } catch (_: TurnSuperseded) {
            // Already answered by a newer message.
        }
    }

    /** The last thing to keep, or null when a tool's answer already ended the turn. */
    private fun converse(asked: TurnAsked): SetupContent? {
        val history = jdbcSetupMessages.list(asked.sessionId, asked.accountId).map { it.content }
        val offered = history.filterIsInstance<BooksFound>().flatMap { found -> found.books.map { it.ref } }
        val turn = SetupTurn(asked.accountId, asked.sessionId, offered)
        val messages = mutableListOf(ChatMessage(Role.SYSTEM, SetupPrompt.SYSTEM))
        messages += setupTools.transcript(history)
        for (call in 1..SetupTurnPolicy.MAX_MODEL_CALLS) {
            val next = step(asked = asked, turn = turn, messages = messages, call = call)
            if (next is Next.End) return next.content
        }
        return failure(SetupError.NO_ANSWER)
    }

    private sealed interface Next {
        data object Continue : Next

        /** [content] is what ends the turn, or null when a tool's answer already did. */
        data class End(
            val content: SetupContent?,
        ) : Next
    }

    /**
     * One model call of the turn, and the tools it asked for. A turn completes only on `stop` with
     * text, or on `tool_calls` that asked for answering tools alone and had one accepted. That call's
     * arguments are the answer, checked by the server: sending its result back for a `stop` would bill
     * a whole call more and tell the model nothing new. Any other ending (`length`, `content_filter`,
     * …) never completes: asked again, the same prompt would most likely end the same way, so it fails.
     */
    private fun step(
        asked: TurnAsked,
        turn: SetupTurn,
        messages: MutableList<ChatMessage>,
        call: Int,
    ): Next {
        val mayUseTools = SetupTurnPolicy.mayUseTools(call)
        val reply = ask(asked, messages, mayUseTools)
        val usedTools = reply.finishReason == FinishReason.TOOL_CALLS && reply.toolCalls.isNotEmpty()
        return when {
            reply.finishReason == FinishReason.STOP -> {
                Next.End(saidOf(reply))
            }

            // Cut at the cap, filtered, or tools asked for when only words were allowed.
            !usedTools || !mayUseTools -> {
                Next.End(failure(SetupError.NO_ANSWER))
            }

            else -> {
                messages += runTools(asked, turn, reply)
                if (turn.answered) Next.End(null) else Next.Continue
            }
        }
    }

    private fun saidOf(reply: LlmReply): SetupContent =
        reply.text
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let(::GlossSaid) ?: failure(SetupError.NO_ANSWER)

    // Thinking off: it bills output tokens on every call, and with tools offered DeepSeek refuses a
    // call (400) unless every earlier assistant turn's reasoning is kept and sent back with it.
    private fun ask(
        asked: TurnAsked,
        messages: List<ChatMessage>,
        mayUseTools: Boolean,
    ): LlmReply {
        setupTurnWriter.checkDeadline(asked)
        val call =
            LlmCall(
                accountId = asked.accountId,
                purpose = Purpose.SETUP,
                // A copy: the turn's own list grows after the call.
                messages = messages.toList(),
                maxTokens = SetupTurnPolicy.MAX_TOKENS,
                thinking = Thinking.DISABLED,
                tools = setupTools.offered,
                toolChoice = if (mayUseTools) ToolChoice.Auto else ToolChoice.None,
            )
        val reply = llmService.stream(call, onText = {}, isCancelled = { setupTurnWriter.pastDeadline(asked) })
        if (reply.cancelled) throw GlossException(SetupError.TURN_TIMED_OUT)
        return reply
    }

    /** Runs every call the model made, in its order, and keeps them with their answers in one write. */
    private fun runTools(
        asked: TurnAsked,
        turn: SetupTurn,
        reply: LlmReply,
    ): List<ChatMessage> {
        val alone = reply.toolCalls.all { SetupTurnPolicy.Tool.of(it.name)?.answers == true }
        // Checked before each tool too: a batch run past the deadline would pay for searches whose
        // answers a newer message has already made useless.
        val outcomes =
            reply.toolCalls.map { call ->
                setupTurnWriter.checkDeadline(asked)
                setupTools.run(call, turn, alone)
            }
        val called = ToolsCalled(reply.text, reply.toolCalls.map { CalledTool(it.id, it.name, it.arguments) })
        setupTurnWriter.keep(asked, listOf(called) + outcomes.map { it.kept }, done = turn.answered)
        val answers = reply.toolCalls.zip(outcomes) { call, outcome -> ChatMessage.toolResult(call.id, outcome.reply) }
        return listOf(reply.asAssistantTurn()) + answers
    }

    private fun failure(error: ErrorCode) = SearchFailed(error.code, error.message)
}
