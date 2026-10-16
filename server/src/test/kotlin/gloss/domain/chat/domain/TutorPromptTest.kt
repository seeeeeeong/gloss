package gloss.domain.chat.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TutorPromptTest {
    private val item = TutorPrompt.Target(ord = 3, number = "1.1", title = "운영체제가\n할 일")

    @Test
    fun `the system message is the rules then the plan, the same bytes for every turn of a book`() {
        val plan = """{"title":"운영체제","items":[]}"""

        assertThat(TutorPrompt.system(plan)).isEqualTo(TutorPrompt.SYSTEM + "\n" + plan)
        assertThat(TutorPrompt.system(plan)).isEqualTo(TutorPrompt.system(plan))
        assertThat(TutorPrompt.SYSTEM).doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `a turn from an item names it, and carries its post when it goes in`() {
        val ask = TutorPrompt.Ask(item = item, post = "## 학습 목표\n\n본문\n", quote = null, question = " 왜요? ")

        assertThat(TutorPrompt.user(ask)).isEqualTo(
            "<글 i=3>\n## 학습 목표\n\n본문\n</글>\n\n항목: i=3 (1.1 운영체제가 할 일)\n질문: 왜요?",
        )
    }

    @Test
    fun `a picked phrase is one line, and the question keeps its own lines`() {
        val ask = TutorPrompt.Ask(item = item, post = null, quote = "자원\n  할당자", question = "첫 줄\n둘째 줄")

        assertThat(TutorPrompt.user(ask)).isEqualTo("항목: i=3 (1.1 운영체제가 할 일)\n인용: 자원 할당자\n질문: 첫 줄\n둘째 줄")
    }

    @Test
    fun `a turn from no item is the question alone`() {
        val ask = TutorPrompt.Ask(item = null, post = null, quote = null, question = "누구를 위한 책인가요?")

        assertThat(TutorPrompt.user(ask)).isEqualTo("질문: 누구를 위한 책인가요?")
    }

    @Test
    fun `a post goes in only with its item`() {
        assertThatThrownBy { TutorPrompt.Ask(item = null, post = "본문", quote = null, question = "왜요?") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
