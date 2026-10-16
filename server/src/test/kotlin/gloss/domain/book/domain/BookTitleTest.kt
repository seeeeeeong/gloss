package gloss.domain.book.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BookTitleTest {
    @Test
    fun `a title is trimmed`() {
        assertThat(BookTitle.of("  오브젝트  ")?.value).isEqualTo("오브젝트")
    }

    @Test
    fun `a title of only spaces, too long, or with a control character is none`() {
        assertThat(BookTitle.of("   ")).isNull()
        assertThat(BookTitle.of("가".repeat(301))).isNull()
        assertThat(BookTitle.of("오브\u0000젝트")).isNull()
    }

    @Test
    fun `three hundred characters, counted by code point, fit`() {
        assertThat(BookTitle.of("😀".repeat(300))).isNotNull()
    }
}
