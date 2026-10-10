package gloss.book.domain

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
