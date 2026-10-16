package gloss.domain.generation.domain

import java.time.Duration

object RetryPolicy {
    /**
     * Only a fault that may pass by itself runs again: the provider busy or failing, the network.
     * [jitter] in [0, 1) spreads jobs that failed together, so they do not return together.
     */
    fun decide(
        attempt: Int,
        maxAttempts: Int,
        retryable: Boolean,
        jitter: Double,
    ): RetryDecision {
        if (!retryable || attempt >= maxAttempts) return RetryDecision.Fail
        val backoff = BASE.multipliedBy(1L shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(CAP)
        return RetryDecision.Retry(backoff.plusMillis((backoff.toMillis() * jitter / 2).toLong()).coerceAtMost(CAP))
    }

    // A rate limit or a provider fault seldom clears within seconds, and a reader waits for the job
    // anyway; past five minutes a retry is better asked for again.
    private val BASE: Duration = Duration.ofSeconds(30)
    private val CAP: Duration = Duration.ofMinutes(5)
    private const val MAX_DOUBLINGS = 10
}

sealed interface RetryDecision {
    data class Retry(
        val after: Duration,
    ) : RetryDecision

    data object Fail : RetryDecision
}
