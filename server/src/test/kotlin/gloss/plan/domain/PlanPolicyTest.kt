package gloss.plan.domain

import gloss.plan.domain.PlanPolicy.Draft
import gloss.plan.domain.PlanPolicy.DraftExclude
import gloss.plan.domain.PlanPolicy.DraftItem
import gloss.plan.domain.PlanPolicy.DraftTerm
import gloss.plan.domain.PlanPolicy.Kind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanPolicyTest {
    private fun item(
        i: Int?,
        kind: String? = "lesson",
        covers: List<String> = listOf("다루는 것"),
        excludes: List<DraftExclude> = emptyList(),
        example: String? = "",
    ) = DraftItem(i = i, kind = kind, covers = covers, excludes = excludes, example = example)

    private fun draft(
        items: List<DraftItem>,
        audience: String? = "초중급 개발자",
        glossary: List<DraftTerm> = emptyList(),
    ) = Draft(audience = audience, glossary = glossary, items = items)

    // Contents of [itemCount] items at one level, unless a test gives each item's parent.
    private fun accepted(
        draft: Draft,
        itemCount: Int,
        parents: List<Int?> = List(itemCount) { null },
    ): PlanPolicy.Plan = (PlanPolicy.accept(draft, parents) as PlanDecision.Accepted).plan

    private fun refused(
        draft: Draft,
        itemCount: Int,
    ): PlanDecision.Reason = (PlanPolicy.accept(draft, List(itemCount) { null }) as PlanDecision.Refused).reason

    @Test
    fun `the cap grows with the contents, and contents past the bound get none`() {
        assertThat(PlanPolicy.capFor(100)).isEqualTo(34_000)
        assertThat(PlanPolicy.capFor(400)).isEqualTo(124_000)
        assertThat(PlanPolicy.capFor(401)).isNull()
        assertThat(PlanPolicy.capFor(0)).isNull()
    }

    @Test
    fun `a reply that plans every item is the plan, in the contents' order`() {
        val reply =
            draft(
                items =
                    listOf(
                        item(1, covers = listOf(" 의존성  문제 ", "의존성 문제", ""), example = " 영화관 "),
                        item(i = 0, kind = "overview", covers = emptyList(), example = null),
                    ),
            )

        val plan = accepted(reply, itemCount = 2, parents = listOf(null, 0))

        assertThat(plan.audience).isEqualTo("초중급 개발자")
        assertThat(plan.items.map { it.ord }).containsExactly(0, 1)
        assertThat(plan.items[0].kind).isEqualTo(Kind.OVERVIEW)
        assertThat(plan.items[0].example).isNull()
        assertThat(plan.items[1].covers).containsExactly("의존성 문제")
        assertThat(plan.items[1].example).isEqualTo("영화관")
    }

    @Test
    fun `an item the reply leaves out refuses the whole plan`() {
        assertThat(refused(draft(listOf(item(0), item(2))), itemCount = 3)).isEqualTo(PlanDecision.Reason.MISSING_ITEM)
        assertThat(refused(draft(emptyList()), itemCount = 1)).isEqualTo(PlanDecision.Reason.MISSING_ITEM)
    }

    @Test
    fun `an item the contents do not have is dropped, and one given twice is read the first time`() {
        val reply = draft(listOf(item(0, covers = listOf("처음")), item(0, covers = listOf("다시")), item(7), item(null)))

        val plan = accepted(reply, itemCount = 1)

        assertThat(plan.items.single().covers).containsExactly("처음")
    }

    @Test
    fun `a reply without an audience, with a kind unknown, or with a lesson that covers nothing is refused`() {
        assertThat(refused(draft(listOf(item(0)), audience = "  "), 1)).isEqualTo(PlanDecision.Reason.NO_AUDIENCE)
        assertThat(refused(draft(listOf(item(0, kind = "quiz"))), 1)).isEqualTo(PlanDecision.Reason.UNKNOWN_KIND)
        assertThat(refused(draft(listOf(item(0, kind = null))), 1)).isEqualTo(PlanDecision.Reason.UNKNOWN_KIND)
        assertThat(refused(draft(listOf(item(0, covers = listOf(" ")))), 1)).isEqualTo(PlanDecision.Reason.EMPTY_LESSON)
    }

    @Test
    fun `an overview of an item with nothing under it is read as a lesson, or refused when it covers nothing`() {
        val reply = draft(listOf(item(0, kind = "overview"), item(1, kind = "overview")))

        val plan = accepted(reply, itemCount = 2)
        val empty = draft(listOf(item(i = 0, kind = "overview", covers = emptyList(), example = null)))

        assertThat(plan.items.map { it.kind }).containsExactly(Kind.LESSON, Kind.LESSON)
        assertThat(refused(empty, itemCount = 1)).isEqualTo(PlanDecision.Reason.EMPTY_LESSON)
    }

    @Test
    fun `an item with nothing to learn keeps nothing but its kind`() {
        val reply = draft(listOf(item(i = 0, kind = "none", covers = listOf("감사"), example = "꽃")))

        val item = accepted(reply, itemCount = 1).items.single()

        assertThat(item).isEqualTo(
            PlanPolicy.Item(ord = 0, kind = Kind.NONE, covers = emptyList(), excludes = emptyList(), example = null),
        )
    }

    @Test
    fun `what is left out points at another item, or nowhere when the pointer is wrong, three at most`() {
        val excludes =
            listOf(
                DraftExclude(" ", 1),
                DraftExclude("응집도", 1),
                DraftExclude("결합도", 0),
                DraftExclude("다형성", 9),
                DraftExclude("상속", 1),
            )
        val reply = draft(listOf(item(0, excludes = excludes), item(1)))

        val kept = accepted(reply, itemCount = 2).items[0].excludes

        assertThat(kept).containsExactly(
            PlanPolicy.Exclude("응집도", 1),
            PlanPolicy.Exclude("결합도", null),
            PlanPolicy.Exclude("다형성", null),
        )
    }

    @Test
    fun `what runs past a bound is cut, not refused`() {
        val reply =
            draft(
                items = listOf(item(0, covers = List(9) { "다루는 것 $it" } + "가".repeat(200))),
                audience = "독".repeat(400),
                glossary = List(70) { DraftTerm(term = "용어 $it", en = null, means = "뜻", at = 0) },
            )
        val long = draft(listOf(item(0, covers = listOf("가".repeat(200)))))

        val plan = accepted(reply, itemCount = 1)

        assertThat(plan.audience).hasSize(200)
        assertThat(plan.glossary).hasSize(60)
        assertThat(plan.items.single().covers).hasSize(6)
        assertThat(
            accepted(long, itemCount = 1)
                .items
                .single()
                .covers
                .single(),
        ).hasSize(120)
    }

    @Test
    fun `a term needs its name and meaning, is kept once, and is defined at an item the contents have`() {
        val glossary =
            listOf(
                DraftTerm(term = "캡슐화", en = " encapsulation ", means = "구현을\n감추는 일", at = 1),
                DraftTerm(term = "캡슐화", en = null, means = "다른 뜻", at = 0),
                DraftTerm(term = "다형성", en = "", means = "여러 형태", at = 5),
                DraftTerm(term = "상속", en = null, means = null, at = 0),
                DraftTerm(term = null, en = null, means = "이름 없는 뜻", at = 0),
            )

        val terms = accepted(draft(listOf(item(0), item(1)), glossary = glossary), itemCount = 2).glossary

        assertThat(terms).containsExactly(
            PlanPolicy.Term(term = "캡슐화", en = "encapsulation", means = "구현을 감추는 일", at = 1),
            PlanPolicy.Term(term = "다형성", en = null, means = "여러 형태", at = null),
        )
    }
}
