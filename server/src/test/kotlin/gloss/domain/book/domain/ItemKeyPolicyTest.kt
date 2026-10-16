package gloss.domain.book.domain

import gloss.domain.book.domain.ItemKeyPolicy.Entry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemKeyPolicyTest {
    @Test
    fun `a key is the number and the title, lowercased, with spacing and width evened out`() {
        assertThat(ItemKeyPolicy.keysOf(listOf(Entry("1.2", "  Spring   ＭＶＣ  ", parent = null))))
            .containsExactly("1.2 spring mvc")
    }

    @Test
    fun `the same section gets the same key in another version`() {
        val first = ItemKeyPolicy.keysOf(listOf(Entry("1장", "시작하기", null), Entry(null, "부록", null)))
        val second = ItemKeyPolicy.keysOf(listOf(Entry("1장", "시작하기 ", null), Entry(null, "부록", null)))

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `an unnumbered section is named within its parent`() {
        val keys =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1장", "객체", null), Entry(null, "정리", 0), Entry("2장", "설계", null), Entry(null, "정리", 2)),
            )

        assertThat(keys).containsExactly("1장 객체", "1장 객체 > 정리", "2장 설계", "2장 설계 > 정리")
    }

    @Test
    fun `a summary added to one chapter does not move another chapter's`() {
        val before =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1장", "객체", null), Entry("2장", "설계", null), Entry(null, "정리", 1)),
            )
        val after =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1장", "객체", null), Entry(null, "정리", 0), Entry("2장", "설계", null), Entry(null, "정리", 2)),
            )

        assertThat(after.last()).isEqualTo(before.last())
    }

    @Test
    fun `a repeat under one parent gets a suffix, and one that only looks like a suffix does not collide`() {
        assertThat(
            ItemKeyPolicy.keysOf(listOf(Entry(null, "정리", null), Entry(null, "정리", null), Entry(null, "정리~2", null))),
        ).containsExactly("정리", "정리~2", "정리\\~2")
    }

    @Test
    fun `any unicode space keeps the key, a line separator or a no-break space alike`() {
        val plain = ItemKeyPolicy.keysOf(listOf(Entry("1.1", "티켓 판매", null)))

        assertThat(ItemKeyPolicy.keysOf(listOf(Entry("1.1", "티켓 판매", null)))).isEqualTo(plain)
        assertThat(ItemKeyPolicy.keysOf(listOf(Entry("1.1", "티켓 판매", null)))).isEqualTo(plain)
    }

    @Test
    fun `a title that normalizing lengthens still fits the column, and repeats of it stay apart`() {
        val keys = ItemKeyPolicy.keysOf(listOf(Entry(null, "½".repeat(500), null), Entry(null, "½".repeat(500), null)))

        assertThat(keys.map { it.codePointCount(0, it.length) }).allMatch { it <= 600 }
        assertThat(keys.toSet()).hasSize(2)
    }

    @Test
    fun `children of a parent whose key fills the column stay apart without counting in order`() {
        val keys =
            ItemKeyPolicy.keysOf(
                listOf(Entry(null, "½".repeat(194), null), Entry(null, "정리", 0), Entry(null, "요약", 0)),
            )

        assertThat(keys.drop(1)).doesNotHaveDuplicates().noneMatch { "~" in it }
        assertThat(keys.map { it.codePointCount(0, it.length) }).allMatch { it <= 600 }
    }

    @Test
    fun `a long unnumbered title keeps its chapter, so one added to another chapter does not move it`() {
        val long = "½".repeat(194)
        val before =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1장", "객체", null), Entry("2장", "설계", null), Entry(null, long, 1)),
            )
        val after =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1장", "객체", null), Entry(null, long, 0), Entry("2장", "설계", null), Entry(null, long, 2)),
            )

        assertThat(after[1]).isNotEqualTo(after[3])
        assertThat(after.last()).isEqualTo(before.last())
    }

    @Test
    fun `a numbered heading repeated under two parts is named by its part, so adding one does not move the other`() {
        val before = ItemKeyPolicy.keysOf(listOf(Entry("1부", "a", null), Entry("2부", "b", null), Entry("1장", "소개", 1)))
        val after =
            ItemKeyPolicy.keysOf(
                listOf(Entry("1부", "a", null), Entry("1장", "소개", 0), Entry("2부", "b", null), Entry("1장", "소개", 2)),
            )

        assertThat(after).containsExactly("1부 a", "1부 a > 1장 소개", "2부 b", "2부 b > 1장 소개")
        assertThat(after.last()).isEqualTo(before.last())
    }

    @Test
    fun `a title holding the path separator is not taken for a path`() {
        val top = ItemKeyPolicy.keysOf(listOf(Entry(null, "A > B", null)))
        val nested = ItemKeyPolicy.keysOf(listOf(Entry(null, "A", null), Entry(null, "B", 0)))

        assertThat(top.single()).isNotEqualTo(nested.last())
    }

    @Test
    fun `a repeat added before a title that ends like a suffix does not move it`() {
        val before = ItemKeyPolicy.keysOf(listOf(Entry(null, "정리", null), Entry(null, "정리~2", null)))
        val after =
            ItemKeyPolicy.keysOf(
                listOf(Entry(null, "정리", null), Entry(null, "정리", null), Entry(null, "정리~2", null)),
            )

        assertThat(after.last()).isEqualTo(before.last())
    }
}
