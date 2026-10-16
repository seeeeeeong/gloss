package gloss.domain.setup.domain

import java.time.Duration

/**
 * How far the model may go answering one reader message. Every call is on the reader's key, so the
 * bounds are the server's, never the model's.
 */
object SetupTurnPolicy {
    /**
     * A recommendation takes about four calls (search the web, look the candidates up, recommend);
     * six leave room for a recommendation refused and written again. The last one may not ask for a
     * tool, so the turn ends in words.
     */
    const val MAX_MODEL_CALLS = 6

    // One look-up per candidate book, five at most, and one more for a title the reader corrects.
    const val MAX_BOOK_SEARCHES = 6

    // Each is the operator's money and a few thousand prompt tokens of the reader's: one on the
    // subject, one more on a narrower angle.
    const val MAX_WEB_SEARCHES = 2

    /**
     * A tool call is under 200 tokens and a five-book recommendation in Korean about 800; 2,000 is
     * room for both, and the reservation holds no more than $0.0024 of output a call.
     */
    const val MAX_TOKENS = 2_000

    /** Six calls of a few seconds each with searches between them fit well inside; a stalled one does not. */
    val DEADLINE: Duration = Duration.ofMinutes(3)

    enum class Tool(
        val code: String,
        /** Whether running it ends the turn: what the reader is shown. */
        val answers: Boolean,
    ) {
        FIND_BOOKS("find_books", answers = false),
        SEARCH_WEB("search_web", answers = false),
        RECOMMEND("recommend", answers = true),
        SHOW_BOOKS("show_books", answers = true),
        ;

        companion object {
            fun of(code: String): Tool? = entries.firstOrNull { it.code == code }
        }
    }

    /** Whether one more call of [tool] fits, after [used] calls of it in this turn. */
    fun admits(
        tool: Tool,
        used: Int,
    ): Boolean =
        when (tool) {
            Tool.FIND_BOOKS -> used < MAX_BOOK_SEARCHES
            Tool.SEARCH_WEB -> used < MAX_WEB_SEARCHES
            Tool.RECOMMEND, Tool.SHOW_BOOKS -> true
        }

    /** The [call]th model call of a turn, from 1, may still ask for tools; the last one may not. */
    fun mayUseTools(call: Int): Boolean = call < MAX_MODEL_CALLS
}
