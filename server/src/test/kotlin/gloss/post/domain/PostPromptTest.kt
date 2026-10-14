package gloss.post.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PostPromptTest {
    private val plan = """{"book":{"title":"오브젝트"},"items":[{"i":0},{"i":1}]}"""

    @Test
    fun `the plan comes first, byte for byte, and the section last`() {
        val prompt = PostPrompt.user(plan, PostPrompt.Target(ord = 1, number = "1.1", title = "티켓 판매 애플리케이션"))

        assertThat(prompt).isEqualTo("$plan\n\n작성 대상: i=1 (1.1 티켓 판매 애플리케이션)")
    }

    @Test
    fun `two sections of a book share the prompt up to its last line`() {
        val first = PostPrompt.user(plan, PostPrompt.Target(ord = 0, number = "1장", title = "객체, 설계"))
        val second = PostPrompt.user(plan, PostPrompt.Target(ord = 1, number = null, title = "찾아\n보기"))

        assertThat(first.substringBeforeLast('\n')).isEqualTo(second.substringBeforeLast('\n'))
        assertThat(second).endsWith("작성 대상: i=1 (찾아 보기)")
    }

    @Test
    fun `the rules give a post its form, and leave the title to the page`() {
        assertThat(PostPrompt.SYSTEM)
            .contains("## 학습 목표")
            .contains("## 요약")
            .contains("## 핵심 용어")
            .contains("글 제목은 쓰지 않습니다")
            .contains("`-다` 체")
    }
}
