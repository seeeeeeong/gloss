package gloss.domain.book.domain

import gloss.domain.book.domain.ReadablePolicy.Entry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReadablePolicyTest {
    private fun readable(vararg entries: Entry): List<Int> = ReadablePolicy.readable(entries.toList())

    @Test
    fun `unnumbered items before the first numbered one and after the last are left out, with their own`() {
        val kept =
            readable(
                Entry(null, null), // 추천사
                Entry(null, null), // 들어가며
                Entry(1, null), // 이 책의 구성
                Entry(null, "1"), // 서론
                Entry(3, "1.1"), // 할 일
                Entry(null, null), // 마치며
            )

        assertThat(kept).containsExactly(3, 4)
    }

    @Test
    fun `the unnumbered items after the last numbered one are left out, keeping the sections in it`() {
        val kept =
            readable(
                Entry(null, "1"), // 서론
                Entry(0, null), // 정리
                Entry(null, null), // 용어집
                Entry(null, null), // 미주
                Entry(null, null), // 찾아보기
            )

        assertThat(kept).containsExactly(0, 1)
    }

    @Test
    fun `an unnumbered part that holds the first numbered chapter is kept`() {
        val kept =
            readable(
                Entry(null, null), // 들어가며
                Entry(null, null), // 개관
                Entry(1, "1"), // 서론
                Entry(2, "1.1"),
                Entry(2, "1.2"),
                Entry(1, "2"), // 구조
                Entry(5, "2.1"),
            )

        assertThat(kept).containsExactly(1, 2, 3, 4, 5, 6)
    }

    @Test
    fun `a book that numbers nothing, or opens and closes on a numbered item, keeps every item`() {
        assertThat(readable(Entry(null, null), Entry(null, null))).containsExactly(0, 1)
        assertThat(readable(Entry(null, "Part 1"), Entry(0, null), Entry(null, "2"))).containsExactly(0, 1, 2)
        assertThat(readable()).isEmpty()
    }

    @Test
    fun `an empty number is no number`() {
        assertThat(readable(Entry(null, ""), Entry(null, "1"))).containsExactly(1)
    }
}
