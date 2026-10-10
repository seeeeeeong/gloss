package gloss.llm.application

import gloss.apikey.application.ApiKeyService
import gloss.apikey.application.KeyAccess
import gloss.budget.application.BudgetService
import gloss.budget.application.Outcome
import gloss.budget.application.Reservation
import gloss.budget.application.ReserveRequest
import gloss.budget.application.UsageReport
import gloss.common.error.GlossException
import gloss.llm.domain.ChatRequest
import gloss.llm.domain.ChatResult
import gloss.llm.domain.LlmCallFailed
import gloss.llm.domain.LlmGateway
import gloss.llm.domain.PromptSizePolicy
import gloss.llm.domain.SettlementDecision
import gloss.llm.domain.SettlementPolicy
import gloss.llm.domain.TokenUsage
import gloss.llm.domain.chat.FinishReason
import gloss.llm.domain.chat.Thinking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * The one way a model is called: the reader's key is opened, the call is reserved, then sent,
 * then settled with what it used or released if it cost nothing. Every path ends the
 * reservation. No transaction is held across the call; budget's are each their own.
 */
@Service
class LlmService(
    private val apiKeyService: ApiKeyService,
    private val budgetService: BudgetService,
    private val llmGateway: LlmGateway,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [onText] gets pieces as they arrive, and they are provisional: a reply that did not finish
     * with STOP (cut at the cap, filtered, cancelled) is not an answer, and a caller that showed
     * the pieces discards or marks them. [onText] must return promptly: it hands a piece off and
     * never waits on a slow client, since nothing can interrupt it from here and a call held past
     * budget's expiry is settled at its full reservation. Nothing is stored here.
     *
     * Runs outside any caller's transaction: no connection or lock is held through the call, and
     * the reservation and settlement commit on their own, so a caller rolling back cannot erase
     * what the provider billed.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun stream(
        call: LlmCall,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): LlmReply {
        if (isCancelled()) return CANCELLED
        val key = usableKey(call)
        val promptTokens =
            PromptSizePolicy.upperBoundTokens(
                call.messages.map { it.content },
                thinking =
                    call.thinking == Thinking.ENABLED,
            )
        val maxTokens = capFor(call, promptTokens)
        val reservation = reserve(call = call, key = key, promptTokens = promptTokens, maxTokens = maxTokens)
        var ended = false
        try {
            if (isCancelled()) {
                budgetService.release(reservation, Outcome.CANCELLED_BEFORE_OUTPUT)
                ended = true
                return CANCELLED
            }
            val request =
                ChatRequest(
                    model = DeepSeekModel.ID,
                    messages = call.messages,
                    maxTokens = maxTokens,
                    thinking = call.thinking,
                )
            val reply =
                try {
                    val result =
                        llmGateway.stream(
                            secret = key.secret,
                            request = request,
                            onText = onText,
                            isCancelled = isCancelled,
                        )
                    // The call went out and was answered: from here a failure leaves the reservation
                    // open, to expire at its full amount, rather than release what was billed.
                    ended = true
                    settleCompleted(reservation, result, Estimate(promptTokens, maxTokens.toLong()))
                    LlmReply(result.text, result.finishReason)
                } catch (e: LlmCallFailed) {
                    // A call that may have billed is settled or left to expire, never released again;
                    // one that billed nothing is only done once its release went through.
                    if (chargeOf(e) != SettlementDecision.Release) ended = true
                    endFailed(reservation, e, Estimate(promptTokens, maxTokens.toLong()))
                    ended = true
                    if (e.kind == LlmCallFailed.Kind.REJECTED_KEY) {
                        apiKeyService.markRejected(call.accountId, key.keyId)
                    }
                    when (e.kind) {
                        LlmCallFailed.Kind.CANCELLED -> CANCELLED

                        // The caller's own error, after paying for what the call may have billed.
                        LlmCallFailed.Kind.CALLER_FAILED -> throw e.cause ?: e

                        else -> throw GlossException(LlmError.of(e.kind), e)
                    }
                }
            ended = true
            // A side record: failing to write it never takes back an answer the reader has paid for.
            runCatching { apiKeyService.recordUse(key.keyId) }
                .onFailure { log.warn("Could not record a key's use: {}", it.javaClass.name) }
            return reply
        } finally {
            // Whatever escaped above, the reservation does not stay open.
            if (!ended) budgetService.release(reservation, Outcome.ERROR)
        }
    }

    private fun reserve(
        call: LlmCall,
        key: KeyAccess.Usable,
        promptTokens: Long,
        maxTokens: Int,
    ): Reservation =
        budgetService.reserve(
            ReserveRequest(
                accountId = call.accountId,
                apiKeyId = key.keyId,
                purpose = call.purpose,
                model = DeepSeekModel.ID,
                prices = DeepSeekModel.PRICES,
                promptTokens = promptTokens,
                maxTokens = maxTokens.toLong(),
            ),
        )

    private fun capFor(
        call: LlmCall,
        promptTokens: Long,
    ): Int =
        PromptSizePolicy.fittedCap(
            requested = call.maxTokens,
            promptTokens = promptTokens,
            contextTokens = DeepSeekModel.CONTEXT_TOKENS,
            maxOutputTokens = DeepSeekModel.MAX_OUTPUT_TOKENS,
        ) ?: throw GlossException(LlmError.PROMPT_TOO_LONG)

    private fun chargeOf(failure: LlmCallFailed): SettlementDecision =
        SettlementPolicy.decide(
            usageReported = failure.usage != null,
            outputStarted = failure.outputStarted,
            promptMayHaveBilled = failure.promptMayHaveBilled,
        )

    private fun usableKey(call: LlmCall): KeyAccess.Usable =
        when (val access = apiKeyService.access(call.accountId)) {
            is KeyAccess.Usable -> access
            KeyAccess.Missing -> throw GlossException(LlmError.NO_KEY)
            KeyAccess.Unreadable -> throw GlossException(LlmError.KEY_UNREADABLE)
            KeyAccess.Rejected -> throw GlossException(LlmError.KEY_REJECTED)
        }

    /** What the call can have cost at most, for when the provider never said. */
    private data class Estimate(
        val promptTokens: Long,
        val maxTokens: Long,
    )

    private fun settleCompleted(
        reservation: Reservation,
        result: ChatResult,
        estimate: Estimate,
    ) {
        val outcome =
            when (result.finishReason) {
                FinishReason.STOP -> Outcome.STOP
                FinishReason.LENGTH -> Outcome.LENGTH
                FinishReason.CONTENT_FILTER -> Outcome.CONTENT_FILTER
                else -> Outcome.ERROR
            }
        budgetService.settle(reservation, reportOf(result.usage, outcome, estimate.full()))
    }

    private fun endFailed(
        reservation: Reservation,
        failure: LlmCallFailed,
        estimate: Estimate,
    ) {
        val cancelled = failure.kind == LlmCallFailed.Kind.CANCELLED
        val outcome =
            when {
                cancelled && failure.outputStarted -> Outcome.CANCELLED_AFTER_OUTPUT
                cancelled -> Outcome.CANCELLED_BEFORE_OUTPUT
                else -> Outcome.ERROR
            }
        when (chargeOf(failure)) {
            SettlementDecision.Reported -> {
                budgetService.settle(reservation, reportOf(failure.usage, outcome, estimate.full()))
            }

            SettlementDecision.FullEstimate -> {
                budgetService.settle(reservation, reportOf(null, outcome, estimate.full()))
            }

            SettlementDecision.PromptEstimate -> {
                budgetService.settle(reservation, reportOf(null, outcome, estimate.promptOnly()))
            }

            SettlementDecision.Release -> {
                budgetService.release(reservation, outcome)
            }
        }
    }

    private fun Estimate.full() =
        TokenUsage(
            cacheHitTokens = 0,
            cacheMissTokens = promptTokens,
            completionTokens = maxTokens,
            reasoningTokens = null,
        )

    private fun Estimate.promptOnly() =
        TokenUsage(cacheHitTokens = 0, cacheMissTokens = promptTokens, completionTokens = 0, reasoningTokens = null)

    private fun reportOf(
        usage: TokenUsage?,
        outcome: Outcome,
        estimate: TokenUsage,
    ): UsageReport {
        val counted = usage ?: estimate
        return UsageReport(
            cacheHitTokens = counted.cacheHitTokens,
            cacheMissTokens = counted.cacheMissTokens,
            completionTokens = counted.completionTokens,
            outcome = outcome,
            reasoningTokens = counted.reasoningTokens,
            usageEstimated = usage == null,
        )
    }

    private companion object {
        val CANCELLED = LlmReply(text = "", finishReason = FinishReason.UNKNOWN, cancelled = true)
    }
}
