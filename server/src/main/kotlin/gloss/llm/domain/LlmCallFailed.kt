package gloss.llm.domain

/**
 * A call that did not finish. [outputStarted] says whether the model began answering, thinking
 * included, so whether the call may have billed output. [promptMayHaveBilled] says whether the
 * request reached the provider and was not refused with a status, so whether the prompt may
 * have billed even with no answer.
 */
class LlmCallFailed(
    val kind: Kind,
    val outputStarted: Boolean,
    val usage: TokenUsage? = null,
    val promptMayHaveBilled: Boolean = outputStarted,
) : RuntimeException(kind.name) {
    enum class Kind {
        /** 401: the key is wrong or was revoked at the provider. */
        REJECTED_KEY,

        /** 402: the reader's provider account is out of balance. */
        INSUFFICIENT_BALANCE,

        /** 429. */
        RATE_LIMITED,

        /** Any other answer that was not a stream: a 4xx we caused or a 5xx. */
        PROVIDER_ERROR,

        /** No answer, a dropped connection, or nothing for longer than the idle timeout. */
        NETWORK,

        /** The caller stopped it; the connection was closed so generation stops billing. */
        CANCELLED,

        /** The caller's own handler threw while pieces were arriving; its exception is the cause. */
        CALLER_FAILED,
    }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
