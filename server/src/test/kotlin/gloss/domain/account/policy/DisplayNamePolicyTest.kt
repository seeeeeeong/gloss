package gloss.domain.account.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DisplayNamePolicyTest {
    @Test
    fun `a name is trimmed`() {
        assertThat(DisplayNamePolicy.normalize("  독자  ")).isEqualTo("독자")
    }

    @Test
    fun `nothing left after trimming, non-breaking spaces included, is refused`() {
        assertThat(DisplayNamePolicy.normalize("  ")).isNull()
    }

    @Test
    fun `fifty characters fit and fifty-one do not, counted as characters`() {
        assertThat(DisplayNamePolicy.normalize("가".repeat(50))).isNotNull()
        assertThat(DisplayNamePolicy.normalize("가".repeat(51))).isNull()
    }

    @Test
    fun `a control character, NUL among them, is refused`() {
        assertThat(DisplayNamePolicy.normalize("A\u0000B")).isNull()
        assertThat(DisplayNamePolicy.normalize("A\nB")).isNull()
    }
}
