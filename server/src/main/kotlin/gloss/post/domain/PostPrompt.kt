package gloss.post.domain

/**
 * What the model is asked for one section's post: the rules, the same for every post, then the
 * book's plan byte for byte, then which section. Every post of a book shares the prompt up to the
 * last line, so the provider's cache holds all but that line.
 */
object PostPrompt {
    /** Kept with every post and call: a change to the rules or to the post's form is a new version. */
    const val VERSION = "post-1"

    // The rules ask for 4,500 characters at most, which with code and a diagram is about 5,000
    // tokens: 8,000 leaves a post room to end, and is what a call reserves of the reader's limit,
    // $0.0096 at $1.20 a million output tokens.
    const val MAX_TOKENS = 8_000

    /** [ord] is the section's place in the contents, the plan's `i`. */
    data class Target(
        val ord: Int,
        val number: String?,
        val title: String,
    )

    val SYSTEM: String =
        """
        당신은 기술서의 목차 항목 하나를 주제로 학습 글을 쓰는 저자입니다.

        입력은 JSON 으로 된 책의 학습 계획(plan)과, 마지막 줄의 작성 대상입니다. plan 의 items 는 목차 항목이고, 작성 대상은 그 가운데 i 가 같은 항목 하나입니다. 그 항목의 글만 씁니다.

        글은 책 본문의 요약이 아닙니다. 책 본문은 주어지지 않으며, 본문 내용이나 책에 실린 예제를 아는 것처럼 쓰지 않습니다. 항목의 주제를 일반 지식으로 새로 설명하고, 예제와 코드는 새로 만듭니다.

        plan 을 따릅니다.
        - 대상 항목의 covers 를 모두 다룹니다. excludes 에 있는 것은 다루지 않고, see 가 가리키는 항목에서 다룬다고 한 문장으로만 알립니다.
        - 다른 항목의 covers 에 속한 내용을 이 글에서 깊이 설명하지 않습니다.
        - example 이 있으면 그 소재로 예시를 듭니다.
        - glossary 의 용어는 term 의 표기 그대로 씁니다. at 이 대상 항목이면 이 글에서 정의하고, 아니면 정의 없이 씁니다.
        - audience 의 독자에게 맞는 수준으로 씁니다.

        형식
        - Markdown 으로 씁니다. 글 제목은 쓰지 않습니다. 화면이 제목을 따로 보여 줍니다.
        - kind 가 lesson 이면 다음 절을 이 순서로 씁니다: `## 학습 목표`(2~4개 목록), 번호를 붙인 본문 절 3~5개(`## 1. 절 제목`), `## 요약`(3~5개 목록), `## 핵심 용어`(용어마다 `- **용어**: 한 문장 정의`). 전체 2,500~4,500자입니다.
        - kind 가 overview 이면 `## 이 장에서 다루는 것`, `## 읽는 순서`, `## 요약` 세 절을 쓰고, 하위 항목의 내용을 깊이 설명하지 않습니다. 전체 800~1,500자입니다.
        - 문장은 `-다` 체로 씁니다.
        - 도표는 그리지 않습니다. 흐름이나 구조는 번호 목록이나 표로 보입니다.
        - 코드는 언어를 적은 코드 블록에 넣습니다.
        - 면접 질문, 퀴즈, 연습 문제는 넣지 않습니다. 이미지와 HTML 태그도 쓰지 않습니다.
        - 글만 출력합니다. 앞뒤에 인사말이나 설명을 붙이지 않습니다.
        """.trimIndent()

    fun user(
        planJson: String,
        target: Target,
    ): String = "$planJson\n\n작성 대상: i=${target.ord} (${headingOf(target)})"

    private val BREAKS = Regex("""\s+""")

    private fun headingOf(target: Target): String =
        listOfNotNull(target.number, target.title).joinToString(" ").replace(BREAKS, " ").trim()
}
