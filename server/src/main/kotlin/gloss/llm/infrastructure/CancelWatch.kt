package gloss.llm.infrastructure

import gloss.llm.domain.LlmCallFailed
import gloss.llm.domain.TokenUsage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Why a connection was closed from outside the reading thread, if it was: a cancel, a failed check, the deadline. */
internal class CancelWatch {
    private val cancelled = AtomicBoolean(false)
    private val error = AtomicReference<Throwable?>(null)
    private val expired = AtomicBoolean(false)

    val closed: Boolean get() = cancelled.get() || error.get() != null || expired.get()

    fun cancel() = cancelled.set(true)

    fun expire() = expired.set(true)

    fun fail(cause: Throwable) = error.set(cause)

    /** The failure a lost connection stands for, the caller's own error first. */
    fun failure(
        lost: Throwable,
        outputStarted: Boolean,
        promptMayHaveBilled: Boolean,
        usage: TokenUsage? = null,
    ): LlmCallFailed {
        val callerError = error.get()
        val kind =
            when {
                callerError != null -> LlmCallFailed.Kind.CALLER_FAILED
                cancelled.get() -> LlmCallFailed.Kind.CANCELLED
                else -> LlmCallFailed.Kind.NETWORK
            }
        return LlmCallFailed(
            kind = kind,
            outputStarted = outputStarted,
            usage = usage,
            promptMayHaveBilled = promptMayHaveBilled,
        ).apply { initCause(callerError ?: lost) }
    }
}
