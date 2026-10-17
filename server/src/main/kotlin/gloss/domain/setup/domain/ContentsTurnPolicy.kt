package gloss.domain.setup.domain

import java.time.Duration

/**
 * How far the model may go looking for one edition's detailed contents. Every call is on the reader's
 * key and every search on the operator's, so the bounds are the server's, never the model's.
 */
object ContentsTurnPolicy {
    /**
     * Search, read a page or two, submit, and submit again after a refusal. The last call must submit
     * what was read, so it is made only once a page has been.
     */
    const val MAX_MODEL_CALLS = 6

    // The operator's money: one search in the book's language, one more on another angle or in English.
    const val MAX_WEB_SEARCHES = 2

    // Each page read stays in every later call's prompt, up to about 13,000 tokens of the reader's.
    const val MAX_PAGE_FETCHES = 3

    /**
     * What one page tells the model and what its titles are checked against: a 300-item contents runs
     * to about 10,000 characters, with room for the page's menus before it.
     */
    const val MAX_PAGE_CHARACTERS = 20_000

    /**
     * submit_toc writes the whole contents: 300 items in Korean are about 6,000 tokens. 8,000 is room
     * for them, and the reservation holds no more than $0.0096 of output a call.
     */
    const val MAX_TOKENS = 8_000

    /** The same as a message's turn, so a session waits on either for as long. */
    val DEADLINE: Duration = SetupTurnPolicy.DEADLINE

    enum class Tool(
        val code: String,
    ) {
        SEARCH_WEB("search_web"),
        FETCH_PAGE("fetch_page"),
        SUBMIT_TOC("submit_toc"),
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
            Tool.SEARCH_WEB -> used < MAX_WEB_SEARCHES
            Tool.FETCH_PAGE -> used < MAX_PAGE_FETCHES
            Tool.SUBMIT_TOC -> true
        }

    enum class Choice {
        /** Any tool, or words to say nothing was found. */
        ANY,

        /** submit_toc only, with thinking off as DeepSeek requires to force a tool. */
        SUBMIT,

        /** No call: the last one could submit nothing, since no page was read. */
        STOP,
    }

    /** What the [call]th model call of a turn, from 1, may do, once a page has been [read] or not. */
    fun choiceOf(
        call: Int,
        read: Boolean,
    ): Choice =
        when {
            call < MAX_MODEL_CALLS -> Choice.ANY
            call == MAX_MODEL_CALLS && read -> Choice.SUBMIT
            else -> Choice.STOP
        }
}
