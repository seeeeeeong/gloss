package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SetupTitlePolicyTest {
    @Test
    fun `the book held or made names the session, whatever the reader asked`() {
        assertThat(SetupTitlePolicy.of("오브젝트", "객체지향 책 추천해 줘")).isEqualTo("오브젝트")
    }

    @Test
    fun `without a book, the reader's first message names it`() {
        assertThat(SetupTitlePolicy.of(null, "객체지향 책 추천해 줘")).isEqualTo("객체지향 책 추천해 줘")
    }

    @Test
    fun `a long message is cut to a line, counted as a reader sees it`() {
        assertThat(SetupTitlePolicy.of(null, "가".repeat(80))).isEqualTo("가".repeat(80))
        assertThat(SetupTitlePolicy.of(null, "가".repeat(81))).isEqualTo("가".repeat(80) + "…")
        assertThat(SetupTitlePolicy.of(null, "😀".repeat(81))).isEqualTo("😀".repeat(80) + "…")
        assertThat(SetupTitlePolicy.of(null, "오브젝트\n그리고 다른 책")).isEqualTo("오브젝트")
    }

    @Test
    fun `nothing said and nothing held names it nothing`() {
        assertThat(SetupTitlePolicy.of(null, null)).isEmpty()
    }
}
