package gloss.domain.post.infrastructure

import gloss.domain.post.domain.PostCheck.Block
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommonMarkOutlineTest {
    private val commonMarkOutline = CommonMarkOutline()

    @Test
    fun `a post's top-level blocks come in order, a heading with its level and words`() {
        val text = "## 학습 목표 ##\n\n본문이다.\n\n제목\n===\n\n## `코드` 와 **강조**\n\n| a | b |\n| - | - |\n| 1 | 2 |"

        assertThat(commonMarkOutline.of(text))
            .containsExactly(
                Block.Heading(2, "학습 목표"),
                Block.Other,
                Block.Heading(1, "제목"),
                Block.Heading(2, "코드 와 강조"),
                Block.Other,
            )
    }

    @Test
    fun `inline HTML in a heading is kept as its source, and an image is nothing, as the page shows them`() {
        assertThat(commonMarkOutline.of("## 학습 <b>목표</b>")).containsExactly(Block.Heading(2, "학습 <b>목표</b>"))
        assertThat(commonMarkOutline.of("## ![학습 목표](https://example.com/a.png)")).containsExactly(Block.Heading(2, ""))
    }

    @Test
    fun `a link's definition is no block of the outline`() {
        assertThat(commonMarkOutline.of("[guide]: https://example.com/guide\n\n## 학습 목표\n\n[guide] 를 본다."))
            .containsExactly(Block.Heading(2, "학습 목표"), Block.Other)
    }

    @Test
    fun `a paragraph underlined with - is a heading, and a rule after a blank line is not`() {
        assertThat(commonMarkOutline.of("요약\n---")).containsExactly(Block.Heading(2, "요약"))
        assertThat(commonMarkOutline.of("본문이다.\n\n---")).containsExactly(Block.Other, Block.Other)
    }

    @Test
    fun `nothing inside code, an HTML block, a list or a quote is a heading of the post's`() {
        val text = "```\n# a\n```\n\n<div>\n## b\n</div>\n\n<pre>\n\n## e\n\n</pre>\n\n- ## c\n\n> # d"

        assertThat(commonMarkOutline.of(text)).doesNotHaveAnyElementsOfTypes(Block.Heading::class.java)
    }
}
