package gloss.domain.book.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TocParserTest {
    private fun parsed(raw: String): List<TocParser.Item> = (TocParser.parse(raw) as TocParser.Result.Parsed).items

    private fun outline(raw: String) = parsed(raw).map { "${"  ".repeat(it.depth - 1)}${it.number ?: "-"} ${it.title}" }

    @Test
    fun `chapters and dotted sections nest by their numbers`() {
        val raw =
            """
            1장 객체, 설계
            1.1 티켓 판매 애플리케이션
            1.1.1 요구사항
            1.2 무엇이 문제인가
            2장 객체지향 프로그래밍
            """.trimIndent()

        assertThat(outline(raw)).containsExactly(
            "1장 객체, 설계",
            "  1.1 티켓 판매 애플리케이션",
            "    1.1.1 요구사항",
            "  1.2 무엇이 문제인가",
            "2장 객체지향 프로그래밍",
        )
    }

    @Test
    fun `parts sit above chapters, and unnumbered lines at the top stay at the top`() {
        val raw =
            """
            들어가며
            1부 기초
            Chapter 1 Getting Started
            1.1 Install
            부록 A 참고 자료
            찾아보기
            """.trimIndent()

        assertThat(outline(raw)).containsExactly(
            "- 들어가며",
            "1부 기초",
            "  Chapter 1 Getting Started",
            "    1.1 Install",
            "  부록 A 참고 자료",
            "- 찾아보기",
        )
    }

    @Test
    fun `a Roman numeral written as one character is a part, the letter I alone is not`() {
        assertThat(outline("Ⅰ 가상화\n제1장 프로세스\nⅡ 병행성\n제2장 스레드\nI 는 로봇\n제3장 락")).containsExactly(
            "Ⅰ 가상화",
            "  제1장 프로세스",
            "Ⅱ 병행성",
            "  제2장 스레드",
            "    - I 는 로봇",
            "  제3장 락",
        )
    }

    @Test
    fun `in contents that indent nothing, unnumbered lines are sections of the chapter above`() {
        val raw =
            """
            들어가며
            제1부. AI 거인들의 전쟁
            제1장. 마침내 GPT-5 등장하다

            GPT-5 시대의 개막
            인류의 마지막 시험

            제2장. 빅3+1
            클로드, 코딩과 글쓰기
            제2부. 다음 무대
            제3장. 에이전트
            도구를 쓰는 모델
            """.trimIndent()

        assertThat(outline(raw)).containsExactly(
            "- 들어가며",
            "제1부 AI 거인들의 전쟁",
            "  제1장 마침내 GPT-5 등장하다",
            "    - GPT-5 시대의 개막",
            "    - 인류의 마지막 시험",
            "  제2장 빅3+1",
            "    - 클로드, 코딩과 글쓰기",
            "제2부 다음 무대",
            "  제3장 에이전트",
            "    - 도구를 쓰는 모델",
        )
    }

    @Test
    fun `an unnumbered line after a numbered section is that section's sibling`() {
        assertThat(outline("1장 시작\n1.1 설치\n정리\n2장 기본기")).containsExactly(
            "1장 시작",
            "  1.1 설치",
            "  - 정리",
            "2장 기본기",
        )
    }

    @Test
    fun `a numbered section goes under the section its number names, past an unnumbered sibling`() {
        val items = parsed("1장 시작\n1.1 설치\n팁\n1.1.1 확인\n1.2 실행")

        assertThat(items.map { it.depth }).containsExactly(1, 2, 2, 3, 2)
        assertThat(items.map { it.parent }).containsExactly(null, 0, 0, 1, 0)
        assertThat(items.map { it.position }).containsExactly(0, 0, 1, 0, 2)
    }

    @Test
    fun `an unnumbered line right under a chapter is its section, and so is the numbered one after it`() {
        val items = parsed("1장 시작\n팁\n1.1 설치\n1.1.1 확인")

        assertThat(items.map { it.depth }).containsExactly(1, 2, 2, 3)
        assertThat(items.map { it.parent }).containsExactly(null, 0, 0, 2)
        assertThat(items.map { it.position }).containsExactly(0, 0, 1, 0)
    }

    @Test
    fun `a bare number under a labelled chapter is its section, and what it indents sits under it`() {
        val raw =
            """
            1장: 객체, 설계
            01. 티켓 판매 애플리케이션 구현하기
            02. 무엇이 문제인가
               예상을 빗나가는 코드
            2장: 객체지향 프로그래밍
            01. 영화 예매 시스템
            """.trimIndent()

        assertThat(outline(raw)).containsExactly(
            "1장 객체, 설계",
            "  01 티켓 판매 애플리케이션 구현하기",
            "  02 무엇이 문제인가",
            "    - 예상을 빗나가는 코드",
            "2장 객체지향 프로그래밍",
            "  01 영화 예매 시스템",
        )
    }

    @Test
    fun `in contents that indent nothing, a chapter's bare numbers and closing lines are its sections`() {
        val raw = "Chapter 01 운영체제의 시작\n1. 운영체제 개념\n2. 운영체제의 태동\n요약\n연습문제\nChapter 02 컴퓨터 시스템\n1. 하드웨어\n요약"

        assertThat(outline(raw)).containsExactly(
            "Chapter 01 운영체제의 시작",
            "  1 운영체제 개념",
            "  2 운영체제의 태동",
            "  - 요약",
            "  - 연습문제",
            "Chapter 02 컴퓨터 시스템",
            "  1 하드웨어",
            "  - 요약",
        )
    }

    @Test
    fun `dotted numbers under a bare section go one deeper, bare numbers outside a chapter stay chapters`() {
        assertThat(outline("1장 시작\n1. 설치\n1.1 내려받기\n2. 실행\n1부 응용\n1 Introduction\n1.1 Scope")).containsExactly(
            "1장 시작",
            "  1 설치",
            "    1.1 내려받기",
            "  2 실행",
            "1부 응용",
            "  1 Introduction",
            "    1.1 Scope",
        )
    }

    @Test
    fun `back matter after the last chapter returns to the top, a chapter's own closing section does not`() {
        assertThat(outline("1장 시작\n설치\n마치며\n2장 끝\n배포\n마치며\n용어집\n찾아보기")).containsExactly(
            "1장 시작",
            "  - 설치",
            "  - 마치며",
            "2장 끝",
            "  - 배포",
            "- 마치며",
            "- 용어집",
            "- 찾아보기",
        )
    }

    @Test
    fun `a chapter label alone on its line is a chapter titled by it`() {
        assertThat(outline("1부 기초\n1장 시작\n설치\n2장\n배포")).containsExactly(
            "1부 기초",
            "  1장 시작",
            "    - 설치",
            "  - 2장",
            "    - 배포",
        )
    }

    @Test
    fun `an unnumbered line indented under another is its child`() {
        val raw = "1장 시작하기\n    설치\n    첫 프로그램\n\t\t디버깅\n2장 기본기"

        assertThat(outline(raw)).containsExactly(
            "1장 시작하기",
            "  - 설치",
            "  - 첫 프로그램",
            "    - 디버깅",
            "2장 기본기",
        )
    }

    @Test
    fun `no level is skipped, so every item below the top has a parent`() {
        val items = parsed("1장 시작\n1.1.1 너무 깊은 절\n1.2 다음 절")

        assertThat(items.map { it.depth }).containsExactly(1, 2, 2)
        assertThat(items.map { it.parent }).containsExactly(null, 0, 0)
        assertThat(items.map { it.position }).containsExactly(0, 0, 1)
    }

    @Test
    fun `bullets, blank lines and a label without a title are taken as written`() {
        assertThat(outline("- 1장 시작\n\n• 1.1 설치\n2장\n")).containsExactly("1장 시작", "  1.1 설치", "- 2장")
    }

    @Test
    fun `a number inside a title is not a label, nor is a bare number starting an indented line`() {
        val items = parsed("10년 차 개발자의 회고\n3 Ways to Win\n1장 시작\n  3 Ways to Win")

        assertThat(items.map { it.number }).containsExactly(null, "3", "1장", null)
        assertThat(items.map { it.title }).containsExactly("10년 차 개발자의 회고", "Ways to Win", "시작", "3 Ways to Win")
        assertThat(items.map { it.depth }).containsExactly(1, 1, 1, 2)
    }

    @Test
    fun `each item carries the key that follows it across versions`() {
        assertThat(parsed("1장 시작하기\n1.1 설치").map { it.key }).containsExactly("1장 시작하기", "1장 시작하기 > 1.1 설치")
    }

    @Test
    fun `contents of only blank lines, too many lines, or a line too long are refused`() {
        assertThat(TocParser.parse("x\n")).isInstanceOf(TocParser.Result.Parsed::class.java)
        assertThat(TocParser.parse("- \n")).isEqualTo(TocParser.Result.Empty)
        assertThat(
            TocParser.parse("x\n".repeat(2_001)),
        ).isEqualTo(TocParser.Result.TooManyItems)
        assertThat(TocParser.parse("가".repeat(501))).isEqualTo(TocParser.Result.LineTooLong)
    }

    @Test
    fun `a part that first comes late leaves the chapters before it as siblings`() {
        assertThat(outline("1장 시작\n2장 계속\n2부 심화\n3장 끝")).containsExactly(
            "1장 시작",
            "2장 계속",
            "2부 심화",
            "  3장 끝",
        )
    }

    @Test
    fun `contents deeper than six levels are refused rather than flattened`() {
        val sevenLevels = "1 A\n1.1 B\n1.1.1 C\n1.1.1.1 D\n1.1.1.1.1 E\n1.1.1.1.1.1 F\n1.1.1.1.1.1.1 G"

        assertThat(TocParser.parse(sevenLevels)).isEqualTo(TocParser.Result.TooDeep)
        assertThat(parsed(sevenLevels.substringBeforeLast("\n")).map { it.depth }).containsExactly(1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `an unnumbered section's key names its chapter`() {
        assertThat(parsed("1장 객체\n  정리\n2장 설계\n  정리").map { it.key })
            .containsExactly("1장 객체", "1장 객체 > 정리", "2장 설계", "2장 설계 > 정리")
    }

    @Test
    fun `a tab between a number and its title is a space`() {
        assertThat(outline("1장\t시작\n1.1\t설치")).containsExactly("1장 시작", "  1.1 설치")
    }

    @Test
    fun `a number too long to be a label is part of the title`() {
        val line = "1".repeat(51) + " 제목"

        assertThat(parsed(line).single().number).isNull()
        assertThat(parsed(line).single().title).isEqualTo(line)
    }

    @Test
    fun `a line is held to five hundred characters whole, its number included`() {
        val tooLong = "1.1 " + "가".repeat(497)

        assertThat(TocParser.parse(tooLong)).isEqualTo(TocParser.Result.LineTooLong)
        assertThat(parsed(tooLong.dropLast(1)).single().number).isEqualTo("1.1")
    }

    @Test
    fun `a number deeper than six levels is refused, even on the first line`() {
        assertThat(TocParser.parse("1.2.3.4.5.6.7 깊은 절")).isEqualTo(TocParser.Result.TooDeep)
    }

    @Test
    fun `a line's indent and trailing space count toward its five hundred characters`() {
        assertThat(TocParser.parse(" ".repeat(499) + "제목")).isEqualTo(TocParser.Result.LineTooLong)
        assertThat(TocParser.parse("제목" + " ".repeat(499))).isEqualTo(TocParser.Result.LineTooLong)
    }
}
