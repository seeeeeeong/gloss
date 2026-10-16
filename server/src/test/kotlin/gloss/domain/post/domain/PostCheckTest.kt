package gloss.domain.post.domain

import gloss.domain.post.domain.PostCheck.Failure
import gloss.domain.post.domain.PostCheck.Form
import gloss.domain.post.infrastructure.CommonMarkOutline
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Judged on outlines the page's own reading gives, so each case is the Markdown a model may write.
class PostCheckTest {
    private val commonMarkOutline = CommonMarkOutline()

    private val lesson =
        """
        ## 학습 목표

        - 의존성 문제를 설명한다.
        - 변경에 닫힌 설계를 고른다.

        ## 1. 티켓 판매

        본문이다.

        ```python
        # A comment, not a title
        ## Nor a section
        print("영화관")
        ```

        ### 1.1 더 작은 절

        본문이다.

        ## 2. 의존성

        본문이다.

        ## 3. 설계 고치기

        본문이다.

        ## 요약

        - 요약이다.

        ## 핵심 용어

        - **의존성**: 한 객체가 다른 객체를 아는 것이다.
        """.trimIndent()

    private val overview =
        """
        ## 이 장에서 다루는 것

        본문이다.

        ## 읽는 순서

        1. 먼저 읽는다.

        ## 요약

        - 요약이다.
        """.trimIndent()

    private fun failures(
        text: String,
        form: Form = Form.LESSON,
        finishReason: String = "stop",
    ) = judge(text, form, finishReason).failures

    private fun judge(
        text: String,
        form: Form,
        finishReason: String = "stop",
    ) = PostCheck.judge(
        finishReason = finishReason,
        text = text,
        outline = commonMarkOutline.of(text),
        form = form,
    )

    @Test
    fun `a lesson and an overview in the form the prompt asks for pass`() {
        assertThat(judge(lesson, Form.LESSON).passed).isTrue()
        assertThat(judge(overview, Form.OVERVIEW).passed).isTrue()
    }

    @Test
    fun `an answer that did not end by itself fails, however well formed`() {
        listOf("length", "content_filter", "insufficient_system_resource", "unknown").forEach {
            assertThat(failures(lesson, finishReason = it)).containsExactly(Failure.FINISH)
        }
    }

    @Test
    fun `an empty answer fails as empty, and nothing more is looked for`() {
        assertThat(failures("")).containsExactly(Failure.EMPTY)
        assertThat(failures(" \n\t", finishReason = "length")).containsExactlyInAnyOrder(Failure.FINISH, Failure.EMPTY)
    }

    @Test
    fun `the goals open a lesson as a heading of either kind, and a preface before them fails`() {
        val goals = "## 학습 목표\n"

        assertThat(failures(lesson)).isEmpty()
        assertThat(failures("학습 목표\n---\n" + lesson.removePrefix(goals))).isEmpty()
        assertThat(failures("들어가며.\n\n학습 목표\n---\n" + lesson.removePrefix(goals)))
            .containsExactly(Failure.OPENING)
        assertThat(failures("학습 목표\n===\n" + lesson.removePrefix(goals)))
            .containsExactlyInAnyOrder(Failure.TITLE, Failure.OPENING, Failure.SECTIONS)
    }

    @Test
    fun `a heading's words are what the page shows, raw HTML as written and a code span as its code`() {
        assertThat(failures(lesson.replace("## 학습 목표", "## 학습 <b>목표</b>")))
            .containsExactlyInAnyOrder(Failure.OPENING, Failure.SECTIONS)
        assertThat(failures(lesson.replace("## 2. 의존성", "## 2. `Theater` 의 의존성"))).isEmpty()
        assertThat(failures(lesson.replace("## 학습 목표", "## ![학습 목표](https://example.com/a.png)")))
            .containsExactlyInAnyOrder(Failure.OPENING, Failure.SECTIONS)
    }

    @Test
    fun `a link's definition is not shown, so it opens nothing`() {
        assertThat(failures("[guide]: https://example.com/guide\n\n$lesson")).isEmpty()
    }

    @Test
    fun `a lesson must open with its goals`() {
        val noGoals = lesson.replace("## 학습 목표\n\n- 의존성 문제를 설명한다.\n- 변경에 닫힌 설계를 고른다.\n\n", "")

        assertThat(failures(noGoals)).containsExactlyInAnyOrder(Failure.OPENING, Failure.SECTIONS)
        assertThat(failures("이 글은 의존성을 다룬다.\n\n$lesson")).containsExactly(Failure.OPENING)
    }

    @Test
    fun `a title of the post's own fails, the page shows the section's`() {
        assertThat(failures("# 티켓 판매 애플리케이션\n\n$lesson")).containsExactlyInAnyOrder(Failure.TITLE, Failure.OPENING)
        assertThat(failures(lesson.replace("### 1.1", "# 1.1"))).containsExactly(Failure.TITLE)
    }

    @Test
    fun `a missing, extra or misplaced section fails`() {
        assertThat(failures(lesson.replace("## 핵심 용어", "핵심 용어"))).containsExactly(Failure.SECTIONS)
        assertThat(failures(lesson.replace("## 요약", "## 정리"))).containsExactly(Failure.SECTIONS)
        assertThat(failures(lesson.replace("## 3. 설계 고치기", "본문이다."))).containsExactly(Failure.SECTIONS)
        assertThat(failures(lesson.replace("## 2. 의존성", "## 3. 의존성"))).containsExactly(Failure.SECTIONS)
        assertThat(failures("$lesson\n\n## 면접 질문\n\n- 질문이다.")).containsExactly(Failure.SECTIONS)
        assertThat(failures(overview, Form.LESSON)).containsExactlyInAnyOrder(Failure.OPENING, Failure.SECTIONS)
        assertThat(failures(lesson, Form.OVERVIEW)).containsExactlyInAnyOrder(Failure.OPENING, Failure.SECTIONS)
    }

    @Test
    fun `a lesson has three to five numbered sections`() {
        val six =
            lesson.replace(
                "## 요약",
                "## 4. 넷\n\n본문이다.\n\n## 5. 다섯\n\n본문이다.\n\n## 6. 여섯\n\n본문이다.\n\n## 요약",
            )
        val five = six.replace("## 6. 여섯\n\n본문이다.\n\n", "")

        assertThat(failures(five)).isEmpty()
        assertThat(failures(six)).containsExactly(Failure.SECTIONS)
    }

    @Test
    fun `a heading's closing hashes are not its words`() {
        assertThat(failures(lesson.replace("## 요약", "## 요약 ##"))).isEmpty()
    }

    @Test
    fun `a title underlined with = is a title too, outside code`() {
        val underlined = lesson.replace("## 1. 티켓 판매", "티켓 판매 애플리케이션\n===\n\n## 1. 티켓 판매")
        val inCode = lesson.replace("print(\"영화관\")", "print(\"영화관\")\n===")

        assertThat(failures(underlined)).containsExactly(Failure.TITLE)
        assertThat(failures(inCode)).isEmpty()
    }

    @Test
    fun `a paragraph underlined with - is a section, and a rule after a blank line is not`() {
        assertThat(failures(lesson.replace("## 요약", "요약\n---"))).isEmpty()
        assertThat(failures(lesson.replace("## 2. 의존성", "---\n\n## 2. 의존성"))).isEmpty()
        assertThat(failures(lesson.replace("본문이다.\n\n## 2.", "본문이다.\n---\n\n## 2."))).containsExactly(Failure.SECTIONS)
        assertThat(failures(lesson.replace("- 요약이다.", "- 요약이다.\n---"))).isEmpty()
    }

    @Test
    fun `a fence is closed only by one of its own kind, indented three spaces at most`() {
        fun closedBy(fence: String) = lesson.replace("## 2. 의존성", "```kotlin\nval a = 1\n$fence\n\n## 2. 의존성")

        assertThat(failures(closedBy("```"))).isEmpty()
        assertThat(failures(closedBy("   ````"))).isEmpty()
        assertThat(failures(closedBy("    ```"))).containsExactly(Failure.SECTIONS)
        assertThat(failures(closedBy("~~~"))).containsExactly(Failure.SECTIONS)
        assertThat(failures(closedBy("``` kotlin"))).containsExactly(Failure.SECTIONS)
    }

    @Test
    fun `headings in an HTML block are not shown as headings, so are no sections`() {
        val goals = "## 학습 목표\n\n- 의존성 문제를 설명한다.\n\n"
        val inHtml = goals + "<script type=\"text/markdown\">\n" + lesson.removePrefix(goals) + "\n</script>"

        assertThat(failures(inHtml)).containsExactly(Failure.SECTIONS)
    }

    @Test
    fun `headings in a code block inside a list item are code, and the fence closing it opens nothing`() {
        val listed = "- 예시다.\n\n  ```markdown\n  ## 1. 가짜 절\n  # 가짜 제목\n  ```\n"
        val withListed = lesson.replace("## 2. 의존성", "$listed\n## 2. 의존성")
        val onlyListed =
            "## 학습 목표\n\n- 목표다.\n\n- 예시다.\n\n  ```markdown\n" +
                "  ## 1. 하나\n  ## 2. 둘\n  ## 3. 셋\n  ## 요약\n  ## 핵심 용어\n  ```\n"

        assertThat(failures(withListed)).isEmpty()
        assertThat(failures(onlyListed)).containsExactly(Failure.SECTIONS)
    }

    @Test
    fun `headings in a quote are the quote's, not the post's`() {
        val quoted = lesson.replace("## 3. 설계 고치기", "> ## 3. 설계 고치기")
        val quotedTitle = lesson.replace("### 1.1 더 작은 절", "> # 인용한 제목")

        assertThat(failures(quoted)).containsExactly(Failure.SECTIONS)
        assertThat(failures(quotedTitle)).isEmpty()
    }

    @Test
    fun `a code block left open hides what follows it`() {
        val open = lesson.replace("## 2. 의존성", "```kotlin\nval a = 1\n\n## 2. 의존성")

        assertThat(failures(open)).containsExactly(Failure.SECTIONS)
    }
}
