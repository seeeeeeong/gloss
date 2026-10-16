package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReaderTextTest {
    @Test
    fun `line breaks and runs of spaces become one space, and the ends are trimmed`() {
        assertThat(ReaderText.of("  오브젝트\n\n  코드로   이해하는 \t객체지향 ")?.value).isEqualTo("오브젝트 코드로 이해하는 객체지향")
    }

    @Test
    fun `nothing but spaces is no message`() {
        assertThat(ReaderText.of(" \n\t ")).isNull()
    }

    @Test
    fun `five hundred characters fit, counted as a reader sees them`() {
        assertThat(ReaderText.of("가".repeat(500))).isNotNull()
        assertThat(ReaderText.of("😀".repeat(500))).isNotNull()
        assertThat(ReaderText.of("가".repeat(501))).isNull()
    }

    @Test
    fun `a control character is refused`() {
        assertThat(ReaderText.of("오브젝트\u0000")).isNull()
    }
}
