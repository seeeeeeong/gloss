package gloss.generation.domain

import java.time.Duration

sealed interface RetryDecision {
    data class Retry(
        val after: Duration,
    ) : RetryDecision

    data object Fail : RetryDecision
}
