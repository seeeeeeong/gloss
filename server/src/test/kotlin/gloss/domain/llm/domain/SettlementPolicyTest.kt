package gloss.domain.llm.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SettlementPolicyTest {
    @Test
    fun `usage the provider reported is what is charged, however the call ended`() {
        assertThat(SettlementPolicy.decide(usageReported = true, outputStarted = false, promptMayHaveBilled = false))
            .isEqualTo(SettlementDecision.Reported)
    }

    @Test
    fun `without usage, a call that began answering is charged its whole prompt and cap`() {
        assertThat(SettlementPolicy.decide(usageReported = false, outputStarted = true, promptMayHaveBilled = true))
            .isEqualTo(SettlementDecision.FullEstimate)
    }

    @Test
    fun `without usage or output, a request that reached the provider is charged its prompt`() {
        assertThat(SettlementPolicy.decide(usageReported = false, outputStarted = false, promptMayHaveBilled = true))
            .isEqualTo(SettlementDecision.PromptEstimate)
    }

    @Test
    fun `only a call that cannot have billed is released`() {
        assertThat(SettlementPolicy.decide(usageReported = false, outputStarted = false, promptMayHaveBilled = false))
            .isEqualTo(SettlementDecision.Release)
    }
}
