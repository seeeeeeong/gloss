package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SetupTurnPolicyTest {
    @Test
    fun `book searches stop at six a turn and web searches at two, and answering has no count`() {
        assertThat(SetupTurnPolicy.admits(SetupTurnPolicy.Tool.FIND_BOOKS, used = 5)).isTrue()
        assertThat(SetupTurnPolicy.admits(SetupTurnPolicy.Tool.FIND_BOOKS, used = 6)).isFalse()
        assertThat(SetupTurnPolicy.admits(SetupTurnPolicy.Tool.SEARCH_WEB, used = 1)).isTrue()
        assertThat(SetupTurnPolicy.admits(SetupTurnPolicy.Tool.SEARCH_WEB, used = 2)).isFalse()
        assertThat(SetupTurnPolicy.admits(SetupTurnPolicy.Tool.RECOMMEND, used = 9)).isTrue()
    }

    @Test
    fun `the sixth call of a turn may only answer in words`() {
        assertThat((1..6).map(SetupTurnPolicy::mayUseTools)).containsExactly(true, true, true, true, true, false)
    }

    @Test
    fun `only the tools that answer the reader end a turn`() {
        assertThat(
            SetupTurnPolicy.Tool.entries
                .filter { it.answers }
                .map { it.code },
        ).containsExactly("recommend", "show_books")
        assertThat(SetupTurnPolicy.Tool.of("find_books")).isEqualTo(SetupTurnPolicy.Tool.FIND_BOOKS)
        assertThat(SetupTurnPolicy.Tool.of("delete")).isNull()
    }
}
