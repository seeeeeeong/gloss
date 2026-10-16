package gloss.domain.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LoginThrottlePolicyTest {
    private val now = Instant.parse("2026-10-15T03:00:00Z")

    // Newest first, as the table hands them over.
    private fun failures(
        count: Int,
        apart: Duration = Duration.ofMinutes(1),
    ) = (0 until count).map { now.minus(apart.multipliedBy(it.toLong())) }

    @Test
    fun `four failures for a login id from one address still allow a try`() {
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = failures(4), failuresForIp = failures(4)))
            .isEqualTo(LoginThrottleDecision.Allowed)
    }

    @Test
    fun `the fifth blocks that login id from that address until the oldest of the five leaves the window`() {
        val decision = LoginThrottlePolicy.decide(failuresForLoginIdAndIp = failures(5), failuresForIp = failures(5))

        assertThat(decision).isEqualTo(LoginThrottleDecision.Blocked(now.plus(Duration.ofMinutes(11))))
    }

    @Test
    fun `an address that failed fifty times across login ids is blocked`() {
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = emptyList(), failuresForIp = failures(49)))
            .isEqualTo(LoginThrottleDecision.Allowed)
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = emptyList(), failuresForIp = failures(50)))
            .isInstanceOf(LoginThrottleDecision.Blocked::class.java)
    }

    @Test
    fun `blocked both ways, it lifts when the later of the two does`() {
        val decision =
            LoginThrottlePolicy.decide(
                failuresForLoginIdAndIp = failures(5, apart = Duration.ofMinutes(3)),
                failuresForIp = failures(50, apart = Duration.ofSeconds(10)),
            )

        assertThat(decision).isEqualTo(LoginThrottleDecision.Blocked(now.plusSeconds(410)))
    }

    @Test
    fun `the window is fifteen minutes`() {
        assertThat(LoginThrottlePolicy.WINDOW.toMinutes()).isEqualTo(15)
    }
}
