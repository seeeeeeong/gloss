package gloss.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginThrottlePolicyTest {
    @Test
    fun `four failures for a login id from one address still allow a try`() {
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = 4, failuresForIp = 4))
            .isEqualTo(LoginThrottleDecision.Allowed)
    }

    @Test
    fun `the fifth blocks that login id from that address`() {
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = 5, failuresForIp = 5))
            .isEqualTo(LoginThrottleDecision.Blocked)
    }

    @Test
    fun `an address that failed fifty times across login ids is blocked`() {
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = 0, failuresForIp = 49))
            .isEqualTo(LoginThrottleDecision.Allowed)
        assertThat(LoginThrottlePolicy.decide(failuresForLoginIdAndIp = 0, failuresForIp = 50))
            .isEqualTo(LoginThrottleDecision.Blocked)
    }

    @Test
    fun `the window is fifteen minutes`() {
        assertThat(LoginThrottlePolicy.WINDOW.toMinutes()).isEqualTo(15)
    }
}
