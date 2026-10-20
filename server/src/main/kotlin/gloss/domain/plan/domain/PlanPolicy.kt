package gloss.domain.plan.domain

/**
 * What a model's reply must be to stand as a book's plan, and what the call for it may cost. The
 * reply is the model's word, not yet a plan: each item of the contents must be there once, and what
 * runs past a bound is cut, since every post's prompt carries the whole plan.
 */
object PlanPolicy {
    // A bound on one call's answer and on the prefix every post's prompt carries; a book's contents
    // run to a few hundred items.
    const val MAX_ITEMS = 400

    // An item's entry runs to about 130 tokens as the prompt bounds it, the audience and forty
    // terms to about 2,500: over twice each, so a plan is not cut at its cap, and no more, since
    // the cap is what a call reserves of the reader's limit. At $1.20 a million output tokens a
    // 100-item book reserves $0.041 for its answer and a 400-item one $0.149.
    private const val BASE_TOKENS = 4_000
    private const val TOKENS_PER_ITEM = 300

    // A model counts loosely, so a reply may pass what the prompt asks for before it is cut: by
    // half again in how many, and three times in how long.
    private const val MAX_COVERS = PlanPrompt.COVERS * 3 / 2
    private const val MAX_EXCLUDES = PlanPrompt.EXCLUDES * 3 / 2
    private const val MAX_TERMS = PlanPrompt.TERMS * 3 / 2
    private const val MAX_PHRASE = PlanPrompt.PHRASE * 3
    private const val MAX_EXAMPLE = PlanPrompt.EXAMPLE * 3

    // The prompt asks for one sentence, or one term: a long sentence runs to 200 characters, and a
    // term with what it is short for to 60.
    private const val MAX_SENTENCE = 200
    private const val MAX_NAME = 60

    /** The cap sent with the call; null when the contents are more than one call can plan. */
    fun capFor(items: Int): Int? = if (items in 1..MAX_ITEMS) BASE_TOKENS + TOKENS_PER_ITEM * items else null

    /** A reply as read: any field may be missing or of the wrong kind, and is null or empty then. */
    data class Draft(
        val audience: String?,
        val glossary: List<DraftTerm>,
        val items: List<DraftItem>,
    )

    data class DraftTerm(
        val term: String?,
        val en: String?,
        val means: String?,
        val at: Int?,
    )

    data class DraftItem(
        val i: Int?,
        val kind: String?,
        val covers: List<String>,
        val excludes: List<DraftExclude>,
        val example: String?,
    )

    data class DraftExclude(
        val what: String?,
        val see: Int?,
    )

    /** [items] has one entry for each item planned, in the contents' order. */
    data class Plan(
        val audience: String,
        val glossary: List<Term>,
        val items: List<Item>,
    )

    /** [at] is the ord of the item whose post defines the term. */
    data class Term(
        val term: String,
        val en: String?,
        val means: String,
        val at: Int?,
    )

    data class Item(
        val ord: Int,
        val kind: Kind,
        val covers: List<String>,
        val excludes: List<Exclude>,
        val example: String?,
    )

    /** [see] is the ord of the item whose post covers it instead. */
    data class Exclude(
        val what: String,
        val see: Int?,
    )

    enum class Kind(
        val code: String,
    ) {
        /** A post that explains the item's subject. */
        LESSON("lesson"),

        /** A short post on an item that has items under it, tying their posts together. */
        OVERVIEW("overview"),

        /** Nothing to learn from: a preface, thanks, an index. */
        NONE("none"),
        ;

        companion object {
            fun of(code: String?): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    /** [lines] are the items the plan is asked for, in the contents' order. */
    fun accept(
        draft: Draft,
        lines: List<PlanPrompt.Line>,
    ): PlanDecision {
        val ords = lines.map { it.ord }.toSet()
        val withItems = lines.mapNotNull { it.parent }.toSet()
        val audience = clean(draft.audience, MAX_SENTENCE)
        // An ord the contents do not have is dropped, and one given twice is read the first time.
        val given =
            draft.items
                .filter { it.i != null && it.i in ords }
                .distinctBy { it.i }
                .sortedBy { it.i }
        val items = given.mapNotNull { itemOf(it, ords, withItems) }
        val reason =
            when {
                audience == null -> PlanDecision.Reason.NO_AUDIENCE
                given.size < lines.size -> PlanDecision.Reason.MISSING_ITEM
                items.size < given.size -> PlanDecision.Reason.UNKNOWN_KIND
                items.any { it.kind == Kind.LESSON && it.covers.isEmpty() } -> PlanDecision.Reason.EMPTY_LESSON
                else -> return PlanDecision.Accepted(Plan(audience, termsOf(draft.glossary, ords), items))
            }
        return PlanDecision.Refused(reason)
    }

    private fun itemOf(
        given: DraftItem,
        ords: Set<Int>,
        withItems: Set<Int>,
    ): Item? {
        val ord = checkNotNull(given.i)
        val asked = Kind.of(given.kind) ?: return null
        // An overview ties together the items under it; one of an item with none is read as a lesson.
        val kind = if (asked == Kind.OVERVIEW && ord !in withItems) Kind.LESSON else asked
        if (kind == Kind.NONE) {
            return Item(ord = ord, kind = kind, covers = emptyList(), excludes = emptyList(), example = null)
        }
        return Item(
            ord = ord,
            kind = kind,
            covers =
                given.covers
                    .mapNotNull { clean(it, MAX_PHRASE) }
                    .distinct()
                    .take(MAX_COVERS),
            excludes =
                given.excludes
                    .mapNotNull { exclude ->
                        // Pointing nowhere, or at itself, it still says what to leave out.
                        val see = exclude.see?.takeIf { it in ords && it != ord }
                        clean(exclude.what, MAX_PHRASE)?.let { Exclude(what = it, see = see) }
                    }.take(MAX_EXCLUDES),
            example = clean(given.example, MAX_EXAMPLE),
        )
    }

    private fun termsOf(
        given: List<DraftTerm>,
        ords: Set<Int>,
    ): List<Term> =
        given
            .mapNotNull {
                val term = clean(it.term, MAX_NAME)
                val means = clean(it.means, MAX_SENTENCE)
                if (term == null || means == null) {
                    null
                } else {
                    Term(
                        term = term,
                        en = clean(it.en, MAX_NAME),
                        means = means,
                        at = it.at?.takeIf(ords::contains),
                    )
                }
            }.distinctBy { it.term }
            .take(MAX_TERMS)

    private val SPACES = Regex("""\s+""")

    // Null when nothing is left. One line, counted by code point as the prompt counts characters.
    private fun clean(
        text: String?,
        max: Int,
    ): String? {
        val shown = text.orEmpty().filterNot { it.isISOControl() && !it.isWhitespace() }
        val line = shown.replace(SPACES, " ").trim()
        val end = line.offsetByCodePoints(0, minOf(max, line.codePointCount(0, line.length)))
        return line.substring(0, end).trim().takeIf { it.isNotEmpty() }
    }
}

sealed interface PlanDecision {
    data class Accepted(
        val plan: PlanPolicy.Plan,
    ) : PlanDecision

    data class Refused(
        val reason: Reason,
    ) : PlanDecision

    enum class Reason(
        val code: String,
    ) {
        NO_AUDIENCE("plan.no_audience"),
        MISSING_ITEM("plan.missing_item"),
        UNKNOWN_KIND("plan.unknown_kind"),
        EMPTY_LESSON("plan.empty_lesson"),
    }
}
