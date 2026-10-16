package gloss.domain.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DisplayNameTest {
    @Test
    fun `a name is trimmed`() {
        assertThat(DisplayName.of("  독자  ")?.value).isEqualTo("독자")
    }

    @Test
    fun `nothing left after trimming, non-breaking spaces included, is refused`() {
        assertThat(DisplayName.of("  ")?.value).isNull()
    }

    @Test
    fun `fifty characters fit and fifty-one do not, counted as characters`() {
        assertThat(DisplayName.of("가".repeat(50))?.value).isNotNull()
        assertThat(DisplayName.of("가".repeat(51))?.value).isNull()
    }

    @Test
    fun `a control character, NUL among them, is refused`() {
        assertThat(DisplayName.of("A\u0000B")?.value).isNull()
        assertThat(DisplayName.of("A\nB")?.value).isNull()
    }
}
