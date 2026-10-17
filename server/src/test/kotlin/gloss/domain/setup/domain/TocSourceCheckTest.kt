package gloss.domain.setup.domain

import gloss.domain.setup.domain.TocSourceCheck.Bookshop
import gloss.domain.setup.domain.TocSourceCheck.Item
import gloss.domain.setup.domain.TocSourceCheck.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TocSourceCheckTest {
    private val page =
        """
        운영체제 공룡책 10판 목차 정리
        PART 1. 개관
        Chapter 1 서론
        1.1 운영체제가 할 일
        1.2 컴퓨터 시스템의 구성
        PART 2. 프로세스 관리
        Chapter 3 프로세스
        3.1 프로세스 개념
        """.trimIndent()

    private val found =
        listOf(
            Item(depth = 1, number = "Part 1", title = "개관"),
            Item(depth = 2, number = "1장", title = "서론"),
            Item(depth = 3, number = "1.1", title = "운영체제가  할 일"),
            Item(depth = 3, number = "1.2", title = "컴퓨터 시스템의 구성"),
            Item(depth = 1, number = "Part 2", title = "프로세스 관리"),
            Item(depth = 2, number = "3장", title = "프로세스"),
            Item(depth = 3, number = "3.1", title = "프로세스 개념"),
        )

    private val bookshop = Bookshop(anchors = listOf("개관", "프로세스 관리"), items = 2)

    @Test
    fun `contents copied from the page, with the bookshop's parts in order, are accepted`() {
        assertThat(TocSourceCheck.check(found, page, bookshop)).isEqualTo(Result.Accepted)
    }

    @Test
    fun `case, spacing, punctuation and full-width forms are set aside`() {
        val items = listOf(Item(1, null, "ＰＡＲＴ 1 · 개관!"), Item(2, null, "chapter 1 서론"), Item(2, null, "운영체제가할일"))

        assertThat(TocSourceCheck.check(items, page, Bookshop(emptyList(), items = 0))).isEqualTo(Result.Accepted)
    }

    @Test
    fun `a title the page does not show is refused and named`() {
        val invented = found + Item(depth = 3, number = "3.2", title = "스레드와 병행성")

        val result = TocSourceCheck.check(invented, page, bookshop)

        assertThat((result as Result.Refused).problem).contains("1 titles", "\"스레드와 병행성\"")
    }

    @Test
    fun `titles out of the page's order are refused`() {
        val reordered = listOf(found[0], found[3], found[2]) + found.drop(4)

        val result = TocSourceCheck.check(reordered, page, bookshop)

        assertThat((result as Result.Refused).problem).contains("\"운영체제가  할 일\"", "in this order")
    }

    @Test
    fun `a bookshop part missing from the contents, or out of its order, is refused as another edition`() {
        val missing = TocSourceCheck.check(found, page, Bookshop(listOf("개관", "메모리 관리"), items = 2))
        val swapped = TocSourceCheck.check(found, page, Bookshop(listOf("프로세스 관리", "개관"), items = 2))

        assertThat((missing as Result.Refused).problem).contains("\"메모리 관리\"", "another edition")
        assertThat((swapped as Result.Refused).problem).contains("\"개관\"")
    }

    @Test
    fun `a chapter whose title is within a part's never stands for that part`() {
        val withoutPart = found.filterNot { it.title == "프로세스 관리" }

        val result = TocSourceCheck.check(withoutPart, page, bookshop)

        assertThat((result as Result.Refused).problem).contains("\"프로세스 관리\"")
    }

    @Test
    fun `contents no more detailed than the bookshop's are refused before anything else`() {
        val result = TocSourceCheck.check(found.take(2), page, Bookshop(listOf("개관"), items = 2))

        assertThat((result as Result.Refused).problem).contains("no more detailed than the bookshop's 2")
    }

    @Test
    fun `a title of punctuation alone matches nothing`() {
        val result = TocSourceCheck.check(found + Item(depth = 3, number = null, title = "…"), page, bookshop)

        assertThat(result).isInstanceOf(Result.Refused::class.java)
    }

    @Test
    fun `many missing titles are named five at a time`() {
        val invented = (1..8).map { Item(depth = 1, number = null, title = "없는 장 $it") }

        val result = TocSourceCheck.check(invented, page, Bookshop(emptyList(), items = 0))

        assertThat((result as Result.Refused).problem).startsWith("8 titles").contains("and 3 more")
    }
}
