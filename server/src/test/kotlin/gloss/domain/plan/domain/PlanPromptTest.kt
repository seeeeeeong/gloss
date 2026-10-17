package gloss.domain.plan.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanPromptTest {
    private val subject =
        PlanPrompt.Subject(
            title = "오브젝트",
            authors = listOf("조영호"),
            level = PlanPrompt.Level.COURSE,
            voice = PlanPrompt.Voice.EXPLAIN,
            lines =
                listOf(
                    PlanPrompt.Line(ord = 0, parent = null, depth = 1, number = "1장", title = "객체, 설계"),
                    PlanPrompt.Line(ord = 1, parent = 0, depth = 2, number = "1.1", title = "티켓 판매 애플리케이션"),
                    PlanPrompt.Line(ord = 2, parent = null, depth = 1, number = null, title = "찾아보기"),
                ),
        )

    private val course = "수준: 전공. ${PlanPrompt.Level.COURSE.rule}\n문체: 설명체\n"

    @Test
    fun `the book and its style come first, then each item on a line with its place, its parent's and its number`() {
        assertThat(PlanPrompt.user(subject)).isEqualTo(
            "책: 오브젝트\n저자: 조영호\n" + course + "\n목차\n" +
                "0\t-\t1\t1장\t객체, 설계\n" +
                "1\t0\t2\t1.1\t티켓 판매 애플리케이션\n" +
                "2\t-\t1\t-\t찾아보기\n",
        )
    }

    @Test
    fun `a book without authors has no author line`() {
        assertThat(PlanPrompt.user(subject.copy(authors = emptyList()))).startsWith("책: 오브젝트\n수준: ")
    }

    @Test
    fun `a tab or a line end inside a title does not make another column or item`() {
        val odd = subject.copy(title = "오브\n젝트", lines = listOf(subject.lines[0].copy(title = "객체,\t설계\n2\t-\t1")))

        assertThat(PlanPrompt.user(odd))
            .isEqualTo("책: 오브 젝트\n저자: 조영호\n" + course + "\n목차\n0\t-\t1\t1장\t객체, 설계 2 - 1\n")
    }

    @Test
    fun `the rules ask for JSON and show its shape, as the provider's JSON output needs`() {
        assertThat(PlanPrompt.SYSTEM).contains("JSON").contains("""{"audience":""").contains(""""items":[{"i":0""")
    }

    @Test
    fun `the rules name the bounds a reply is held to`() {
        assertThat(PlanPrompt.SYSTEM)
            .contains("2~4개입니다. 각각 40자 이내")
            .contains("0~2개이고, what 은 40자 이내")
            .contains("30자 이내로")
            .contains("많아도 40개")
    }

    @Test
    fun `the same book is the same prompt, so the rules stay a prefix the cache holds`() {
        assertThat(PlanPrompt.user(subject)).isEqualTo(PlanPrompt.user(subject.copy()))
    }

    @Test
    fun `each level the reader may choose is named with its rule, and the voice by its name alone`() {
        PlanPrompt.Level.entries.forEach { level ->
            assertThat(PlanPrompt.user(subject.copy(level = level))).contains("수준: ${level.label}. ${level.rule}\n")
        }
        PlanPrompt.Voice.entries.forEach { voice ->
            assertThat(PlanPrompt.user(subject.copy(voice = voice))).contains("문체: ${voice.label}\n")
        }
        assertThat(PlanPrompt.Voice.entries.map { it.label }).containsExactly("교과서체", "설명체", "노트체")
        assertThat(PlanPrompt.Level.entries.map { it.label }).containsExactly("입문", "전공", "실무")
    }

    @Test
    fun `the rules say the level shapes the plan and the voice is left to the posts`() {
        assertThat(PlanPrompt.SYSTEM).contains("독자가 고른 수준에 맞추고").contains("plan 의 값은 문체와 상관없이")
    }
}
