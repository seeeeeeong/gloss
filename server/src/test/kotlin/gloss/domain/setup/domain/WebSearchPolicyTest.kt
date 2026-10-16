package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebSearchPolicyTest {
    private val caps = WebSearchPolicy.Caps(perReader = 20, perDay = 100)

    @Test
    fun `a search is allowed under both caps, and refused at either`() {
        assertThat(WebSearchPolicy.decide(readerAsked = 19, allAsked = 99, caps = caps))
            .isEqualTo(WebSearchDecision.ALLOWED)
        assertThat(WebSearchPolicy.decide(readerAsked = 20, allAsked = 20, caps = caps))
            .isEqualTo(WebSearchDecision.READER_CAPPED)
        assertThat(WebSearchPolicy.decide(readerAsked = 0, allAsked = 100, caps = caps))
            .isEqualTo(WebSearchDecision.ALL_CAPPED)
    }
}
