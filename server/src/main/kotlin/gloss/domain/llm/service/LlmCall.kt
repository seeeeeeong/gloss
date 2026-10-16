package gloss.domain.llm.service

import gloss.common.error.GlossException
import gloss.domain.budget.service.Purpose
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.PromptSizePolicy
import gloss.domain.llm.domain.TokenUsage
import gloss.domain.llm.domain.ToolUseDecision
import gloss.domain.llm.domain.ToolUsePolicy
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.domain.chat.ToolDefinition
import java.util.UUID

/**
 * [maxTokens] and [thinking] have no defaults: each caller decides, and pays for, its own. A call
 * with [tools] may end in [FinishReason.TOOL_CALLS]; forcing one with [toolChoice] needs thinking off.
 */
data class LlmCall(
    val accountId: UUID,
    val purpose: Purpose,
    val messages: List<ChatMessage>,
    val maxTokens: Int,
    val thinking: Thinking,
    val format: ReplyFormat = ReplyFormat.TEXT,
    val tools: List<ToolDefinition> = emptyList(),
    val toolChoice: ToolChoice = ToolChoice.Auto,
) {
    internal fun toolUse(): ToolUseDecision {
        val forced =
            when (toolChoice) {
                ToolChoice.Auto, ToolChoice.None -> null
                ToolChoice.Required -> ToolUsePolicy.Forced(name = null)
                is ToolChoice.Named -> ToolUsePolicy.Forced(toolChoice.name)
            }
        val offer =
            ToolUsePolicy.Offer(
                names = tools.map { it.name },
                schemas = tools.map { it.parameters },
                forced = forced,
                thinking = thinking == Thinking.ENABLED,
            )
        return ToolUsePolicy.decide(offer, messages.map(::turnOf))
    }

    private fun turnOf(message: ChatMessage): ToolUsePolicy.Turn =
        when (message.role) {
            Role.ASSISTANT -> {
                ToolUsePolicy.Turn(
                    kind = ToolUsePolicy.Turn.Kind.ASSISTANT,
                    callIds = message.toolCalls.map { it.id },
                    reasoned = message.reasoning != null,
                )
            }

            Role.TOOL -> {
                ToolUsePolicy.Turn(kind = ToolUsePolicy.Turn.Kind.TOOL, answers = message.toolCallId)
            }

            Role.SYSTEM, Role.USER -> {
                ToolUsePolicy.Turn(kind = ToolUsePolicy.Turn.Kind.OTHER)
            }
        }

    /** The most the prompt can bill. A message's tool fields count only when it has them. */
    internal fun promptTokens(): Long {
        val contents = messages.flatMap { listOfNotNull(it.content, it.toolCallId, it.reasoning) }
        val calls = messages.flatMap { it.toolCalls }.map { it.id + it.name + it.arguments }
        val definitions = tools.map { it.name + it.description + it.parameters }
        return PromptSizePolicy.upperBoundTokens(contents, thinking = thinking == Thinking.ENABLED) +
            PromptSizePolicy.toolTokens(definitions = definitions, calls = calls)
    }
}

/**
 * Only a reply that finished with [FinishReason.STOP] is complete; the caller checks before storing it.
 * One that finished with [FinishReason.TOOL_CALLS] carries [toolCalls] for the caller to run and
 * answer. [usage] is null when the provider never reported it, and [reservationId] when nothing was
 * reserved.
 */
data class LlmReply(
    val text: String,
    val finishReason: FinishReason,
    val cancelled: Boolean = false,
    val model: String = DeepSeekModel.ID,
    val usage: ReplyUsage? = null,
    val reservationId: UUID? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val reasoning: String = "",
) {
    /** This reply as the assistant turn of the next call, with its reasoning when it thought. */
    fun asAssistantTurn(): ChatMessage =
        ChatMessage(
            role = Role.ASSISTANT,
            content = text,
            toolCalls = toolCalls,
            reasoning = reasoning.ifEmpty { null },
        )

    companion object {
        internal fun of(
            result: ChatResult,
            reservationId: UUID,
        ) = LlmReply(
            text = result.text,
            finishReason = result.finishReason,
            usage = result.usage?.let(ReplyUsage::of),
            reservationId = reservationId,
            toolCalls = result.toolCalls,
            reasoning = result.reasoning,
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
