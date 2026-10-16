package gloss.domain.llm.domain

import gloss.domain.llm.domain.chat.ToolArgumentsParser

/**
 * Whether a call's tools and the tool turns of its conversation can be sent as they stand,
 * checked before anything is reserved: each of these DeepSeek would refuse with a 400, or would
 * read wrongly (api-docs.deepseek.com, Create Chat Completion and Thinking Mode).
 */
object ToolUsePolicy {
    /** "Function Calling supports multiple functions in one call (up to 128)" (api-docs.deepseek.com/news/news0725). */
    const val MAX_TOOLS = 128

    // As documented for tools[].function.name.
    private val NAME = Regex("[A-Za-z0-9_-]{1,128}")

    /** The tool the model must call, or any tool when [name] is null. */
    data class Forced(
        val name: String?,
    )

    /** What a call offers. [forced] is null when the model may answer without a tool. */
    data class Offer(
        val names: List<String>,
        val schemas: List<String>,
        val forced: Forced?,
        val thinking: Boolean,
    )

    /**
     * A turn as far as tools go: the [callIds] an assistant turn made, in order, and whether it
     * carries its [reasoned] thinking; the call a tool turn [answers].
     */
    data class Turn(
        val kind: Kind,
        val callIds: List<String> = emptyList(),
        val answers: String? = null,
        val reasoned: Boolean = false,
    ) {
        enum class Kind { ASSISTANT, TOOL, OTHER }
    }

    fun decide(
        offer: Offer,
        turns: List<Turn>,
    ): ToolUseDecision {
        val problem = offerProblem(offer) ?: historyProblem(offer, turns)
        return problem?.let(ToolUseDecision::Refused) ?: ToolUseDecision.Accepted
    }

    private fun offerProblem(offer: Offer): ToolUseDecision.Problem? {
        val names = offer.names
        val forced = offer.forced
        return when {
            names.size > MAX_TOOLS -> {
                ToolUseDecision.Problem.TOO_MANY_TOOLS
            }

            names.any { !NAME.matches(it) } -> {
                ToolUseDecision.Problem.BAD_NAME
            }

            names.toSet().size != names.size -> {
                ToolUseDecision.Problem.DUPLICATE_NAME
            }

            offer.schemas.any { ToolArgumentsParser.parse(it) !is ToolArgumentsParser.Result.Parsed } -> {
                ToolUseDecision.Problem.SCHEMA_NOT_AN_OBJECT
            }

            forced == null -> {
                null
            }

            names.isEmpty() -> {
                ToolUseDecision.Problem.NOTHING_TO_FORCE
            }

            forced.name != null && forced.name !in names -> {
                ToolUseDecision.Problem.FORCED_TOOL_NOT_OFFERED
            }

            // "required and named tool choices are not supported in thinking mode": turning thinking
            // off here would change what the call costs, so the caller decides.
            offer.thinking -> {
                ToolUseDecision.Problem.FORCED_WHILE_THINKING
            }

            else -> {
                null
            }
        }
    }

    private fun historyProblem(
        offer: Offer,
        turns: List<Turn>,
    ): ToolUseDecision.Problem? {
        // "for requests carrying the tools parameter, the reasoning_content must be fully passed back
        // to the API in all subsequent requests — even for turns where the model did not perform a
        // tool call" (Thinking Mode guide); without it the request is refused with a 400.
        val reasoningOwed = offer.thinking && offer.names.isNotEmpty()
        return when {
            reasoningOwed && turns.any { it.kind == Turn.Kind.ASSISTANT && !it.reasoned } -> {
                ToolUseDecision.Problem.REASONING_NOT_SENT_BACK
            }

            !resultsFollowCalls(turns) -> {
                ToolUseDecision.Problem.RESULTS_NOT_IN_CALL_ORDER
            }

            else -> {
                null
            }
        }
    }

    // DeepSeek's template pairs a result with a call by its place after the assistant turn, not by
    // its id, so results come right after their turn, one a call, in the calls' order. Reordering
    // them here would send a history other than the one the caller keeps.
    private fun resultsFollowCalls(turns: List<Turn>): Boolean {
        var owed = ArrayDeque<String>()
        var paired = true
        for (turn in turns) {
            val fits = if (turn.kind == Turn.Kind.TOOL) owed.removeFirstOrNull() == turn.answers else owed.isEmpty()
            if (turn.kind != Turn.Kind.TOOL) owed = ArrayDeque(turn.callIds)
            paired = paired && fits
        }
        return paired && owed.isEmpty()
    }
}

sealed interface ToolUseDecision {
    data object Accepted : ToolUseDecision

    data class Refused(
        val problem: Problem,
    ) : ToolUseDecision

    enum class Problem {
        TOO_MANY_TOOLS,
        BAD_NAME,
        DUPLICATE_NAME,
        SCHEMA_NOT_AN_OBJECT,
        NOTHING_TO_FORCE,
        FORCED_TOOL_NOT_OFFERED,
        FORCED_WHILE_THINKING,
        REASONING_NOT_SENT_BACK,
        RESULTS_NOT_IN_CALL_ORDER,
    }
}
