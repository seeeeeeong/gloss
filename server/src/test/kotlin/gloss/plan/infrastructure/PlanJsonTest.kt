package gloss.plan.infrastructure

import gloss.plan.domain.PlanPolicy
import gloss.plan.domain.PlanPrompt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanJsonTest {
    private val planJson = PlanJson()
    private val subject =
        PlanPrompt.Subject(
            title = "오브젝트",
            authors = listOf("조영호"),
            lines =
                listOf(
                    PlanPrompt.Line(ord = 0, parent = null, depth = 1, number = "1장", title = "객체, 설계"),
                    PlanPrompt.Line(ord = 1, parent = 0, depth = 2, number = null, title = "티켓 \"판매\""),
                ),
        )
    private val plan =
        PlanPolicy.Plan(
            audience = "초중급 개발자",
            glossary = listOf(PlanPolicy.Term(term = "캡슐화", en = null, means = "구현을 감추는 일", at = 1)),
            items =
                listOf(
                    PlanPolicy.Item(
                        ord = 0,
                        kind = PlanPolicy.Kind.OVERVIEW,
                        covers = listOf("장의 흐름"),
                        excludes = emptyList(),
                        example = null,
                    ),
                    PlanPolicy.Item(
                        ord = 1,
                        kind = PlanPolicy.Kind.LESSON,
                        covers = listOf("의존성 문제", "책임의 이동"),
                        excludes = listOf(PlanPolicy.Exclude("설계 원칙", 0), PlanPolicy.Exclude("테스트", null)),
                        example = "영화관 매표소",
                    ),
                ),
        )

    @Test
    fun `a plan is written with the book and each item's title, fields in one order, no whitespace`() {
        assertThat(planJson.write(subject, plan)).isEqualTo(
            """{"book":{"title":"오브젝트","authors":["조영호"]},"audience":"초중급 개발자",""" +
                """"glossary":[{"term":"캡슐화","en":null,"means":"구현을 감추는 일","at":1}],""" +
                """"items":[{"i":0,"parent":null,"number":"1장","title":"객체, 설계","kind":"overview",""" +
                """"covers":["장의 흐름"],"excludes":[],"example":null},""" +
                """{"i":1,"parent":0,"number":null,"title":"티켓 \"판매\"","kind":"lesson",""" +
                """"covers":["의존성 문제","책임의 이동"],""" +
                """"excludes":[{"what":"설계 원칙","see":0},{"what":"테스트","see":null}],"example":"영화관 매표소"}]}""",
        )
    }

    @Test
    fun `one plan is always the same bytes`() {
        assertThat(planJson.write(subject, plan)).isEqualTo(PlanJson().write(subject.copy(), plan.copy()))
    }

    @Test
    fun `a reply is read field by field`() {
        val reply =
            """
            {"audience": "초중급 개발자",
             "glossary": [{"term": "캡슐화", "en": "encapsulation", "means": "구현을 감추는 일", "at": 1}],
             "items": [{"i": 0, "kind": "lesson", "covers": ["의존성 문제"],
                        "excludes": [{"what": "설계 원칙", "see": 1}], "example": "영화관"}]}
            """.trimIndent()

        val draft = checkNotNull(planJson.read(reply))

        assertThat(draft.audience).isEqualTo("초중급 개발자")
        assertThat(draft.glossary).containsExactly(
            PlanPolicy.DraftTerm(term = "캡슐화", en = "encapsulation", means = "구현을 감추는 일", at = 1),
        )
        assertThat(draft.items).containsExactly(
            PlanPolicy.DraftItem(
                i = 0,
                kind = "lesson",
                covers = listOf("의존성 문제"),
                excludes = listOf(PlanPolicy.DraftExclude("설계 원칙", 1)),
                example = "영화관",
            ),
        )
    }

    @Test
    fun `a field that is missing or of another kind is left out, not guessed`() {
        val reply =
            """{"audience": 3, "glossary": "none", "items": [{"i": "0", "kind": 1, "covers": ["a", 2, null],
                "excludes": ["b", {"what": 5, "see": 1.5}]}, "junk", {"i": 99999999999}]}"""

        val draft = checkNotNull(planJson.read(reply))

        assertThat(draft.audience).isNull()
        assertThat(draft.glossary).isEmpty()
        assertThat(draft.items).containsExactly(
            PlanPolicy.DraftItem(
                i = null,
                kind = null,
                covers = listOf("a"),
                excludes = listOf(PlanPolicy.DraftExclude(null, null)),
                example = null,
            ),
            PlanPolicy.DraftItem(i = null, kind = null, covers = emptyList(), excludes = emptyList(), example = null),
        )
    }

    @Test
    fun `a list given as an object is no list`() {
        val reply =
            """{"audience": "독자", "glossary": {"a": {"term": "캡슐화", "means": "뜻"}},
                "items": {"first": {"i": 0, "kind": "lesson", "covers": {"a": "다루는 것"},
                                    "excludes": {"a": {"what": "넘기는 것", "see": 1}}}}}"""

        val draft = checkNotNull(planJson.read(reply))

        assertThat(draft.glossary).isEmpty()
        assertThat(draft.items).isEmpty()
        val nested =
            checkNotNull(planJson.read("""{"items": [{"i": 0, "covers": {"a": "b"}, "excludes": {"a": {}}}]}"""))
        assertThat(nested.items.single().covers).isEmpty()
        assertThat(nested.items.single().excludes).isEmpty()
    }

    @Test
    fun `what is not a JSON object is no reply`() {
        assertThat(planJson.read("")).isNull()
        assertThat(planJson.read("[1, 2]")).isNull()
        assertThat(planJson.read("""{"items": [""")).isNull()
        assertThat(planJson.read("목차를 보니 다음과 같습니다")).isNull()
    }
}
