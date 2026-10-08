package gloss.generation.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RetryPolicyTest {
    @Test
    fun `a fault that will not pass by itself is not retried`() {
        assertThat(RetryPolicy.decide(attempt = 1, maxAttempts = 2, retryable = false, jitter = 0.0))
            .isEqualTo(RetryDecision.Fail)
    }

    @Test
    fun `a job that used its attempts is not retried`() {
        assertThat(RetryPolicy.decide(attempt = 2, maxAttempts = 2, retryable = true, jitter = 0.0))
            .isEqualTo(RetryDecision.Fail)
    }

    @Test
    fun `the first retry waits half a minute, and up to half again with jitter`() {
        assertThat(RetryPolicy.decide(attempt = 1, maxAttempts = 2, retryable = true, jitter = 0.0))
            .isEqualTo(RetryDecision.Retry(Duration.ofSeconds(30)))
        assertThat(RetryPolicy.decide(attempt = 1, maxAttempts = 2, retryable = true, jitter = 0.99))
            .isEqualTo(RetryDecision.Retry(Duration.ofMillis(44_850)))
    }

    @Test
    fun `each retry waits twice as long as the last, up to five minutes`() {
        assertThat(RetryPolicy.decide(attempt = 2, maxAttempts = 9, retryable = true, jitter = 0.0))
            .isEqualTo(RetryDecision.Retry(Duration.ofMinutes(1)))
        assertThat(RetryPolicy.decide(attempt = 8, maxAttempts = 9, retryable = true, jitter = 0.99))
            .isEqualTo(RetryDecision.Retry(Duration.ofMinutes(5)))
    }
}
