package gloss.book.infrastructure

import gloss.book.domain.TocParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Yes24ContentsTest {
    private fun outline(raw: String): List<String> {
        val clean = checkNotNull(Yes24Contents.clean(raw))
        val items = (TocParser.parse(clean) as TocParser.Result.Parsed).items
        return items.map { "${"  ".repeat(it.depth - 1)}${it.number ?: "-"} ${it.title}" }
    }

    @Test
    fun `chapter marks, underscore indents and trailing tabs come off, and the numbers place each line`() {
        val raw =
            "▣ 01장: 시작하기\r\n1.1 설치\t\r\n____1.1.1 맥에서\t\r\n____1.1.2 __init__ 파일\t\r\n\r\n" +
                "▣ 02장: 자료형\r\n2.1 숫자\t\r"

        assertThat(outline(raw)).containsExactly(
            "01장 시작하기",
            "  1.1 설치",
            "    1.1.1 맥에서",
            "    1.1.2 __init__ 파일",
            "02장 자료형",
            "  2.1 숫자",
        )
    }

    @Test
    fun `line breaks and bold written as tags are taken off`() {
        val raw =
            "<STRONG>▣ 01장: 소개</STRONG> <br>\r\n1.1 무엇인가 <br>\r\n___1.1.1 역사<br>\r\n<br>\r\n" +
                "<b>▣ 02장: 설치</b><BR/>2.1 준비"

        assertThat(Yes24Contents.clean(raw))
            .isEqualTo("01장: 소개\n1.1 무엇인가\n   1.1.1 역사\n\n02장: 설치\n2.1 준비")
    }

    @Test
    fun `a tag that is a title's own stays, and entities are read`() {
        val raw = "1.1 <script> 태그의 위치<br>1.2 R&amp;D 와 &lt;template&gt;<br>1.3 &amp;lt; 라고 쓰기"

        assertThat(Yes24Contents.clean(raw)).isEqualTo("1.1 <script> 태그의 위치\n1.2 R&D 와 <template>\n1.3 &lt; 라고 쓰기")
    }

    @Test
    fun `one space before a line is not a level, so front matter stays at the top`() {
        val raw = "역자 서문 \r\n 감사의 글 \r\n\r\n 제1장 대화 \r\nⅠ 가상화 \r\n 제2장 프로세스 \r\n제3장 스케줄링 "

        assertThat(outline(raw)).containsExactly(
            "- 역자 서문",
            "- 감사의 글",
            "제1장 대화",
            "Ⅰ 가상화",
            "  제2장 프로세스",
            "  제3장 스케줄링",
        )
    }

    @Test
    fun `an indent of real spaces is kept for lines without numbers`() {
        assertThat(Yes24Contents.clean("들어가며\n    감사의 글\n\n\n\n1부 기초")).isEqualTo("들어가며\n    감사의 글\n\n1부 기초")
    }

    @Test
    fun `nothing but markup is no contents`() {
        assertThat(Yes24Contents.clean(" <br>\r\n<STRONG></STRONG>\r\n▣ \r\n")).isNull()
    }
}
