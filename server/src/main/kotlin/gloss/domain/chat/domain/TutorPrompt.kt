package gloss.domain.chat.domain

/**
 * What the tutor is sent: the rules and the book's plan as one system message, the same bytes for
 * every turn of a book, then the conversation as it was sent, then the reader's new turn. An item's
 * post goes in with the first turn asked from it, so it joins the conversation once and the cache
 * holds it for every turn after; a post in front of the conversation would change with the item and
 * miss the cache for all of it.
 */
object TutorPrompt {
    /** Kept with every reply: a change to the rules or to how a turn is written is a new version. */
    const val VERSION = "tutor-1"

    // The rules ask for a few sentences, about 300 tokens; 2,000 leaves room for a longer answer the
    // reader asks for, and is what a turn reserves of the reader's limit, $0.0024 at $1.20 a million.
    const val MAX_TOKENS = 2_000

    /** [ord] is the item's place in the contents, the plan's `i`. */
    data class Target(
        val ord: Int,
        val number: String?,
        val title: String,
    )

    /** A reader's turn: the item they were on, its post when it goes in now, the phrase they picked. */
    data class Ask(
        val item: Target?,
        val post: String?,
        val quote: String?,
        val question: String,
    ) {
        init {
            require(post == null || item != null) { "A post goes in with the item it belongs to" }
        }
    }

    val SYSTEM: String =
        """
        당신은 Gloss 의 튜터입니다. 독자는 기술서 한 권을 목차 항목마다 하나씩 쓴 학습 글로 읽으면서, 궁금한 것을 묻습니다.

        입력
        - 이 메시지 끝의 JSON 은 책의 학습 계획(plan)입니다. title 은 책 제목, audience 는 독자, items 는 목차 항목이고, 항목은 i 로 가리킵니다.
        - 독자의 메시지는 `항목:` 줄로 읽던 항목을 밝힙니다. 어떤 항목에서 처음 물을 때는 그 항목의 학습 글 전체를 `<글 i=번호>` 와 `</글>` 사이에 함께 보냅니다. 한 번 보낸 글은 다시 보내지 않으니, 대화 앞쪽에서 찾아 읽습니다.
        - `인용:` 줄은 독자가 글에서 골라 물은 구절입니다. `질문:` 다음이 독자가 쓴 말입니다.

        답하는 법
        - 독자가 읽던 항목의 글을 바탕으로 답합니다. 글에 없는 것은 일반 지식으로 설명합니다. 책 본문은 주어지지 않으며, 본문이나 책의 예제를 아는 것처럼 말하지 않습니다.
        - 인용이 있으면 그 구절이 글에서 무슨 뜻인지부터 풉니다.
        - 다른 항목에서 다루는 내용이면 그 항목을 번호와 제목으로 알려 줍니다.
        - 짧게 답합니다. 보통 세 문장에서 여섯 문장이고, 독자가 자세히 청할 때만 길게 씁니다.
        - 일반 텍스트로 씁니다. Markdown 의 제목, 굵게, 목록 기호, 표, 코드 블록 표시를 쓰지 않습니다. 코드가 꼭 필요하면 짧게, 줄을 나눠 씁니다.
        - `-요` 체로 씁니다. 인사말이나 맺음말을 붙이지 않습니다.
        - 책과 상관없는 요청에는 짧게 답하고, 책을 읽는 데 도움이 되는 쪽으로 돌립니다.

        책의 학습 계획
        """.trimIndent()

    fun system(planJson: String): String = "$SYSTEM\n$planJson"

    fun user(ask: Ask): String =
        buildString {
            val item = ask.item
            if (item != null && ask.post != null) {
                append("<글 i=").append(item.ord).append(">\n")
                append(ask.post.trim()).append('\n')
                append("</글>\n\n")
            }
            if (item != null) {
                append("항목: i=")
                    .append(item.ord)
                    .append(" (")
                    .append(headingOf(item))
                    .append(")\n")
            }
            ask.quote?.let { append("인용: ").append(oneLine(it)).append('\n') }
            append("질문: ").append(ask.question.trim())
        }

    private val BREAKS = Regex("""\s+""")

    // A line end inside a heading or a quote would read as the next field.
    private fun oneLine(text: String): String = text.replace(BREAKS, " ").trim()

    private fun headingOf(target: Target): String =
        oneLine(listOfNotNull(target.number, target.title).joinToString(" "))
}
