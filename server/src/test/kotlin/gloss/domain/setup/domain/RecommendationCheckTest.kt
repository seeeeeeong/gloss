package gloss.domain.setup.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecommendationCheckTest {
    private val offered = setOf("101", "102", "103")
    private val pages = mapOf("https://example.com/a" to "비교 글")

    private fun book(
        ref: Any?,
        reason: Any? = "좋은 책이에요.",
        sources: Any? = null,
    ) = mapOf("ref" to ref, "reason" to reason, "sources" to sources)

    private fun check(
        books: Any?,
        intro: Any? = "골랐어요.",
    ) = RecommendationCheck.check(mapOf("intro" to intro, "books" to books), offered, pages)

    @Test
    fun `books a search found are kept in order, each with the pages it may cite`() {
        val result =
            check(
                listOf(
                    book("102", sources = listOf("https://example.com/a", "https://made.up", "https://example.com/a")),
                    book(101L),
                ),
            )

        val accepted = result as RecommendationCheck.Result.Accepted
        assertThat(accepted.intro).isEqualTo("골랐어요.")
        assertThat(accepted.picks.map { it.ref }).containsExactly("102", "101")
        assertThat(accepted.picks.first().sources)
            .containsExactly(RecommendationCheck.Page("https://example.com/a", "비교 글"))
        assertThat(accepted.picks.last().sources).isEmpty()
    }

    @Test
    fun `a book no search found is dropped, and a repeated one kept once`() {
        val result = check(listOf(book("101"), book("999"), book("101"), book("103")))

        assertThat((result as RecommendationCheck.Result.Accepted).picks.map { it.ref }).containsExactly("101", "103")
    }

    @Test
    fun `fewer than two found books is refused, naming the ones not found`() {
        val result = check(listOf(book("101"), book("998"), book("999")))

        assertThat((result as RecommendationCheck.Result.Refused).problem).contains("998, 999").doesNotContain("101")
        assertThat(check(listOf(book("101"), book("101")))).isInstanceOf(RecommendationCheck.Result.Refused::class.java)
    }

    @Test
    fun `more than five, a missing intro, and a book without a reason are refused`() {
        val six = (1..6).map { book("10$it") }

        assertThat(check(six)).isInstanceOf(RecommendationCheck.Result.Refused::class.java)
        assertThat(check(listOf(book("101"), book("102")), intro = " "))
            .isInstanceOf(RecommendationCheck.Result.Refused::class.java)
        assertThat(check(listOf(book("101"), book("102", reason = null))))
            .isInstanceOf(RecommendationCheck.Result.Refused::class.java)
        assertThat(check(listOf(book("101"), book("102", reason = "가".repeat(301)))))
            .isInstanceOf(RecommendationCheck.Result.Refused::class.java)
        assertThat(check("101, 102")).isInstanceOf(RecommendationCheck.Result.Refused::class.java)
        assertThat(check(listOf("101", "102"))).isInstanceOf(RecommendationCheck.Result.Refused::class.java)
    }
}
