package gloss.domain.setup.domain

import java.time.Duration
import java.time.Instant

/** What a setup session moves to after each step a reader takes; the server decides, never a model. */
object SetupPolicy {
    // Past the source's slowest answer (eight seconds) with room to spare: a search still marked
    // after this died before answering, as in a restart, and no longer holds the next one back.
    private val SEARCH_TIMEOUT: Duration = Duration.ofSeconds(30)

    /**
     * Whether a search asked [since] is still being answered: a message waits for it, so answers
     * are kept in the order of the messages they answer.
     */
    fun searching(
        since: Instant?,
        now: Instant,
    ): Boolean = since != null && since.plus(SEARCH_TIMEOUT).isAfter(now)

    enum class State(
        val code: String,
    ) {
        SEARCHING("searching"),
        BOOK_CHOSEN("book_chosen"),
        CONFIRMED("confirmed"),
    }

    enum class Step {
        /** A message, which searches for books; after a pick it searches again and drops the pick. */
        SAY,

        /** An edition picked from what this session's searches found, its contents held for the reader. */
        PICK,

        /** The held contents changed by the reader. */
        EDIT,

        /** The book made from the held contents and confirmed: it is ready to be written from. */
        CONFIRM,
    }

    /** Null when the step is not open in [state]. */
    fun next(
        state: State,
        step: Step,
    ): State? =
        when (state) {
            State.SEARCHING -> {
                when (step) {
                    Step.SAY -> State.SEARCHING
                    Step.PICK -> State.BOOK_CHOSEN
                    Step.EDIT, Step.CONFIRM -> null
                }
            }

            State.BOOK_CHOSEN -> {
                when (step) {
                    Step.SAY -> State.SEARCHING
                    Step.PICK, Step.EDIT -> State.BOOK_CHOSEN
                    Step.CONFIRM -> State.CONFIRMED
                }
            }

            State.CONFIRMED -> {
                null
            }
        }
}
