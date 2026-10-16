package gloss.domain.post.domain

/**
 * Whether a writing is a post to keep: the model finished it, and its outline has the sections
 * [PostPrompt] asks of its kind, in order, under no title of its own. The outline is the post's
 * top-level blocks as the page renders them, read by a CommonMark parser.
 */
object PostCheck {
    /** The plan's kind of the section, which decides the sections its post must have. */
    enum class Form {
        LESSON,
        OVERVIEW,
    }

    /** A top-level block of a post: a heading, `#` or underlined, or anything else. */
    sealed interface Block {
        data class Heading(
            val level: Int,
            val text: String,
        ) : Block

        data object Other : Block
    }

    enum class Failure(
        val code: String,
    ) {
        /** The answer did not end by itself: cut at its cap, filtered, or for a reason unknown. */
        FINISH("finish"),

        /** Nothing but blank space. */
        EMPTY("empty"),

        /** A first-level heading: the page shows the section's title itself. */
        TITLE("title"),

        /** Something before the form's first section: a title, a greeting, a preface. */
        OPENING("opening"),

        /** The second-level headings are not the form's sections in its order. */
        SECTIONS("sections"),
    }

    data class Verdict(
        val failures: Set<Failure>,
    ) {
        val passed: Boolean get() = failures.isEmpty()
    }

    /** [outline] is the post's top-level blocks in order, of [text] as the model wrote it. */
    fun judge(
        finishReason: String,
        text: String,
        outline: List<Block>,
        form: Form,
    ): Verdict {
        val failures =
            buildSet {
                if (finishReason != STOP) add(Failure.FINISH)
                if (text.isBlank()) add(Failure.EMPTY) else addAll(structureOf(outline, form))
            }
        return Verdict(failures)
    }

    private fun structureOf(
        outline: List<Block>,
        form: Form,
    ): Set<Failure> {
        val headings = outline.filterIsInstance<Block.Heading>()
        val sections = headings.filter { it.level == 2 }.map { it.text }
        return buildSet {
            if (headings.any { it.level == 1 }) add(Failure.TITLE)
            if (outline.firstOrNull() != Block.Heading(2, firstOf(form))) add(Failure.OPENING)
            if (!fits(sections, form)) add(Failure.SECTIONS)
        }
    }

    private fun firstOf(form: Form): String =
        when (form) {
            Form.LESSON -> GOALS
            Form.OVERVIEW -> OVERVIEW_SECTIONS.first()
        }

    private fun fits(
        sections: List<String>,
        form: Form,
    ): Boolean =
        when (form) {
            Form.OVERVIEW -> {
                sections == OVERVIEW_SECTIONS
            }

            Form.LESSON -> {
                val body = sections.drop(1).dropLast(LESSON_CLOSING.size)
                sections.firstOrNull() == GOALS &&
                    sections.takeLast(LESSON_CLOSING.size) == LESSON_CLOSING &&
                    body.size in LESSON_BODY &&
                    body.withIndex().all { (i, title) ->
                        NUMBERED.matchEntire(title)?.groupValues?.get(1) == "${i + 1}"
                    }
            }
        }

    // DeepSeek's finish reason of an answer that ended by itself.
    private const val STOP = "stop"

    private const val GOALS = "학습 목표"
    private val LESSON_CLOSING = listOf("요약", "핵심 용어")
    private val LESSON_BODY = 3..5
    private val OVERVIEW_SECTIONS = listOf("이 장에서 다루는 것", "읽는 순서", "요약")

    private val NUMBERED = Regex("""(\d+)\.\s+\S.*""")
}
