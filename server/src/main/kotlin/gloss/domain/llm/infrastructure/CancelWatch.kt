package gloss.domain.llm.infrastructure

import gloss.domain.llm.domain.LlmCallFailed
import gloss.domain.llm.domain.TokenUsage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Why a connection was closed from outside the reading thread, if it was: a cancel, a failed check, the deadline. */
internal class CancelWatch {
    private val cancelled = AtomicBoolean(false)
    private val error = AtomicReference<Throwable?>(null)
    private val expired = AtomicBoolean(false)
    private val ended = AtomicBoolean(false)

    // Set once a failed check is carried by the call's failure or handed out to be logged, so once only.
    private val told = AtomicBoolean(false)

    val closed: Boolean get() = cancelled.get() || error.get() != null || expired.get()

    fun cancel() = cancelled.set(true)

    fun expire() = expired.set(true)

    /** Keeps [cause]; returns it when the call has already ended, since nothing else will carry it then. */
    fun fail(cause: Throwable): Throwable? {
        error.set(cause)
        return cause.takeIf { ended.get() && told.compareAndSet(false, true) }
    }

    /** The call has returned or thrown: returns a check's failure that nothing carried, if one came. */
    fun end(): Throwable? {
        ended.set(true)
        return error.get()?.takeIf { told.compareAndSet(false, true) }
    }

    /** The failure a lost connection stands for, the caller's own error first. */
    fun failure(
        lost: Throwable,
        outputStarted: Boolean,
        promptMayHaveBilled: Boolean,
        usage: TokenUsage? = null,
    ): LlmCallFailed {
        val callerError = error.get()
        if (callerError != null) told.set(true)
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
