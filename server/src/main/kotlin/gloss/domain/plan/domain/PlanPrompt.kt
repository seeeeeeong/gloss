package gloss.domain.plan.domain

/**
 * What the model is asked for a book's plan: the rules, the same for every book, then the book and
 * its contents. Nothing in it varies by request, so the rules are a prefix the provider's cache keeps.
 */
object PlanPrompt {
    /** Kept with every plan and call: a change to the rules, their bounds or the reply's shape is a new version. */
    const val VERSION = "plan-1"

    // What the rules ask a reply to keep to, so the plan stays a small part of each post's prompt:
    // a few noun phrases for an item, and the terms of a book's subject rather than of its every page.
    const val COVERS = 4
    const val EXCLUDES = 2
    const val PHRASE = 40
    const val EXAMPLE = 30
    const val TERMS = 40

    data class Subject(
        val title: String,
        val authors: List<String>,
        val lines: List<Line>,
    )

    /** [ord] is the line's place in the contents, from 0, and [parent] the ord of the line it sits under. */
    data class Line(
        val ord: Int,
        val parent: Int?,
        val depth: Int,
        val number: String?,
        val title: String,
    )

    // The provider's JSON output needs the word "JSON" and an example of the shape in the prompt.
    val SYSTEM: String =
        """
        당신은 기술서의 목차를 보고 학습 글 묶음을 설계하는 편집자입니다.

        독자는 목차의 항목마다 글 하나를 읽습니다. 글은 책 본문을 요약한 것이 아니라, 그 항목의 주제를 새로 설명하는 학습 글입니다. 책 본문은 주어지지 않습니다. 본문 내용이나 책에 실린 예제를 아는 것처럼 쓰지 않고, 목차의 제목과 순서, 그 주제에 대한 일반 지식으로 각 글의 범위를 정합니다.

        입력은 책 정보와 목차입니다. 목차는 한 줄에 한 항목이고, 탭으로 나눈 다섯 칸입니다: 항목 번호 i, 상위 항목의 i(없으면 -), 깊이, 책의 번호 표기(없으면 -), 제목.

        JSON 객체 하나만 출력합니다. 앞뒤에 설명이나 코드 블록 표시를 붙이지 않습니다. 모양은 다음과 같습니다.

        {"audience":"자바로 서비스를 만들어 본 초중급 개발자","glossary":[{"term":"캡슐화","en":"encapsulation","means":"객체의 상태와 구현을 밖에서 보지 못하게 감추는 일","at":1}],"items":[{"i":0,"kind":"overview","covers":["객체지향 설계가 풀려는 문제","이 장의 절들이 이어지는 순서"],"excludes":[],"example":""},{"i":1,"kind":"lesson","covers":["절차적으로 짠 코드의 의존성 문제","책임을 객체로 옮기는 과정"],"excludes":[{"what":"응집도와 결합도의 정의","see":2}],"example":"도서관 대출 시스템"}]}

        규칙
        - items 에는 목차의 모든 항목을 입력 순서대로 한 번씩 넣습니다. i 는 입력의 항목 번호 그대로입니다. 항목을 빼거나 합치거나 새로 만들지 않습니다.
        - kind 는 셋 중 하나입니다. lesson 은 그 주제를 설명하는 글입니다. overview 는 하위 항목이 있는 항목에서, 하위 글들이 무엇을 다루는지 잇는 짧은 안내 글입니다. none 은 옮긴이의 글, 감사의 글, 찾아보기처럼 배울 내용이 없는 항목입니다.
        - covers 는 그 글이 다루는 것 2~${COVERS}개입니다. 각각 ${PHRASE}자 이내의 명사구로 씁니다. none 이면 빈 배열입니다.
        - excludes 는 제목만 보면 여기서 다룰 법하지만 다른 항목의 글이 맡는 것입니다. 0~${EXCLUDES}개이고, what 은 ${PHRASE}자 이내, see 는 그것을 맡는 항목의 i 입니다. 이웃한 글끼리 내용이 겹치지 않게 하는 데 씁니다.
        - example 은 그 글이 예시로 쓸 소재 하나를 ${EXAMPLE}자 이내로 적습니다. 책의 예제가 아니라 새로 고른 소재이고, 이웃한 글과 겹치지 않게 합니다. 예시가 필요 없으면 빈 문자열입니다.
        - audience 는 이 책의 독자를 한 문장으로 적습니다.
        - glossary 는 여러 글에 걸쳐 쓰이는 핵심 용어이고, 많아도 ${TERMS}개입니다. term 은 글에서 쓸 한국어 표기, en 은 영어 원어(없으면 빈 문자열), means 는 한 문장 정의, at 은 그 용어를 처음 정의할 항목의 i 입니다. 한 개념에는 표기를 하나만 정합니다.
        - 모든 값은 한국어로 씁니다. en 과 고유 명사, 코드 식별자만 원어로 둡니다.
        """.trimIndent()

    fun user(subject: Subject): String =
        buildString {
            append("책: ").append(oneLine(subject.title)).append('\n')
            if (subject.authors.isNotEmpty()) {
                append("저자: ").append(subject.authors.joinToString(", ", transform = ::oneLine)).append('\n')
            }
            append("\n목차\n")
            subject.lines.forEach {
                append(it.ord).append('\t')
                append(it.parent?.toString() ?: NONE).append('\t')
                append(it.depth).append('\t')
                append(it.number?.let(::oneLine) ?: NONE).append('\t')
                append(oneLine(it.title)).append('\n')
            }
        }

    private const val NONE = "-"
    private val BREAKS = Regex("""\s+""")

    // A tab or a line end inside a title would read as another column or another item.
    private fun oneLine(text: String): String = text.replace(BREAKS, " ").trim()
}
