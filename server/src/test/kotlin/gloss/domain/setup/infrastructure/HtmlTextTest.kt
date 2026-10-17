package gloss.domain.setup.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration

class HtmlTextTest {
    @Test
    fun `each heading, item and row is a line, and nothing hidden is kept`() {
        val html =
            """
            <!-- <li>숨은 항목</li> -->
            <nav>메뉴</nav><noscript>스크립트를 켜 주세요</noscript>
            <table><tr><td>1장</td><td>서론</td></tr><tr><td>2장</td><td>&lt;구조&gt;</td></tr></table>
            <p>A&amp;B &#x41;&#66; <b>굵게</b><br>다음 줄</p><template><li>틀</li></template>
            """.trimIndent()

        assertThat(HtmlText.of(html).lines()).containsExactly("메뉴", "1장 서론", "2장 <구조>", "A&B AB 굵게", "다음 줄")
    }

    @Test
    fun `a page's title is its title tag, its spacing folded, and none when it has none`() {
        assertThat(HtmlText.titleOf("<TITLE>\n  목차 |  예제 </TITLE>")).isEqualTo("목차 | 예제")
        assertThat(HtmlText.titleOf("<p>제목 없음</p>")).isNull()
    }

    @Test
    fun `a header is a line of its own, not taken for the head`() {
        assertThat(HtmlText.of("<header>머리글</header><p>본문</p>").lines()).containsExactly("머리글", "본문")
    }

    @Test
    fun `a hidden element is dropped as a script is`() {
        val html = "<p hidden>숨김</p><div aria-hidden=\"true\">장식</div><p>보임</p>"

        assertThat(HtmlText.of(html)).isEqualTo("보임")
    }

    @Test
    fun `markup built to stall a parser is read in time linear in its size`() {
        val nested = "<div><span>".repeat(100_000) + "목차" + "</span></div>".repeat(100_000)
        val comment = "<p>앞</p><!--" + "-".repeat(1_000_000)
        val attribute = "<a title=\"" + "<".repeat(1_000_000) + ">링크</a>"
        val tags = "<p<p<p".repeat(300_000)

        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertThat(HtmlText.of(nested)).isEqualTo("목차")
            assertThat(HtmlText.of(comment)).isEqualTo("앞")
            HtmlText.Page(attribute).text
            HtmlText.Page(tags).text
        }
    }
}
