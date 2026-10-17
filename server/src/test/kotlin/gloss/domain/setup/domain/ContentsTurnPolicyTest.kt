package gloss.domain.setup.domain

import gloss.domain.setup.domain.ContentsTurnPolicy.Choice
import gloss.domain.setup.domain.ContentsTurnPolicy.Tool
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentsTurnPolicyTest {
    @Test
    fun `searches stop at two a turn and page reads at three, and submitting has no count`() {
        assertThat(ContentsTurnPolicy.admits(Tool.SEARCH_WEB, used = 1)).isTrue()
        assertThat(ContentsTurnPolicy.admits(Tool.SEARCH_WEB, used = 2)).isFalse()
        assertThat(ContentsTurnPolicy.admits(Tool.FETCH_PAGE, used = 2)).isTrue()
        assertThat(ContentsTurnPolicy.admits(Tool.FETCH_PAGE, used = 3)).isFalse()
        assertThat(ContentsTurnPolicy.admits(Tool.SUBMIT_TOC, used = 9)).isTrue()
    }

    @Test
    fun `the sixth call must submit what was read, and is not made when nothing was`() {
        assertThat((1..5).map { ContentsTurnPolicy.choiceOf(it, read = false) }).containsOnly(Choice.ANY)
        assertThat(ContentsTurnPolicy.choiceOf(6, read = true)).isEqualTo(Choice.SUBMIT)
        assertThat(ContentsTurnPolicy.choiceOf(6, read = false)).isEqualTo(Choice.STOP)
        assertThat(ContentsTurnPolicy.choiceOf(7, read = true)).isEqualTo(Choice.STOP)
    }

    @Test
    fun `a turn's tools are named as the model calls them`() {
        assertThat(Tool.entries.map { it.code }).containsExactly("search_web", "fetch_page", "submit_toc")
        assertThat(Tool.of("find_books")).isNull()
    }
}
