package gloss.domain.setup.domain

/**
 * Whether a recommendation the model wrote can be shown. A book whose ref no book search in this
 * session returned is dropped, as is a cited page no web search in this turn returned; the model's
 * word is never enough. Too few books left is refused, with a [Result.Refused.problem] the model reads.
 */
object RecommendationCheck {
    // A recommendation is a choice: one book is a title search's answer (show_books).
    const val MIN_BOOKS = 2

    // The prototype shows three; five still reads as a choice, not a wall of books to scroll.
    const val MAX_BOOKS = 5

    // The prototype's intro is two sentences of about 100 characters; four times that is room, not an essay.
    const val MAX_INTRO = 400

    // One or two sentences under the meta line, about 60 in the prototype; past 300 it pushes the list apart.
    const val MAX_REASON = 300

    // The prototype's source row is one link and a "+N" pill: three fit one line at phone width.
    const val MAX_SOURCES = 3

    sealed interface Result {
        data class Accepted(
            val intro: String,
            val picks: List<Pick>,
        ) : Result

        data class Refused(
            val problem: String,
        ) : Result
    }

    data class Pick(
        val ref: String,
        val reason: String,
        val sources: List<Page>,
    )

    data class Page(
        val url: String,
        val title: String,
    )

    /**
     * [offered] are the refs this session's book searches returned; [pages] the titles of the URLs
     * this turn's web searches returned, by URL.
     */
    fun check(
        arguments: Map<String, Any?>,
        offered: Set<String>,
        pages: Map<String, String>,
    ): Result {
        val intro = text(arguments["intro"], MAX_INTRO) ?: return refused("intro must be 1 to $MAX_INTRO characters")
        val books = arguments["books"] as? List<*>
        if (books == null || books.size > MAX_BOOKS) return refused("recommend $MIN_BOOKS to $MAX_BOOKS books")
        val written = books.map { pickOf(it, pages) ?: return refused(SHAPE) }
        val picks = written.filter { it.ref in offered }.distinctBy { it.ref }
        if (picks.size >= MIN_BOOKS) return Result.Accepted(intro, picks)
        val unknown = written.map { it.ref }.filter { it !in offered }
        val problem =
            if (unknown.isEmpty()) {
                "recommend $MIN_BOOKS to $MAX_BOOKS different books"
            } else {
                "find_books did not return these refs in this conversation: ${unknown.joinToString()}; " +
                    "recommend $MIN_BOOKS to $MAX_BOOKS books it returned"
            }
        return refused(problem)
    }

    private fun pickOf(
        book: Any?,
        pages: Map<String, String>,
    ): Pick? {
        val fields = book as? Map<*, *> ?: return null
        val ref = refOf(fields["ref"]) ?: return null
        val reason = text(fields["reason"], MAX_REASON) ?: return null
        val cited = (fields["sources"] as? List<*>).orEmpty()
        val sources =
            cited
                .filterIsInstance<String>()
                .map { it.trim() }
                .distinct()
                .mapNotNull { url -> pages[url]?.let { Page(url, it) } }
                .take(MAX_SOURCES)
        return Pick(ref = ref, reason = reason, sources = sources)
    }

    // A model may write a numeric ref as a number.
    private fun refOf(value: Any?): String? =
        when (value) {
            is String -> value.trim().takeIf { it.isNotEmpty() }
            is Long -> value.toString()
            else -> null
        }

    private fun text(
        value: Any?,
        max: Int,
    ): String? =
        (value as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.codePointCount(0, it.length) <= max }

    private fun refused(problem: String) = Result.Refused(problem)

    private const val SHAPE = "each book needs a ref and a reason of 1 to $MAX_REASON characters"
}
