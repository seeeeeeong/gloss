package gloss.domain.setup.domain

import java.time.Duration
import java.time.Instant

/** What a setup session moves to after each step a reader takes; the server decides, never a model. */
object SetupPolicy {
    // Past a model turn's own deadline with room for its last write: a turn still marked after this
    // died before answering, as in a restart, and no longer holds the next message back.
    private val SEARCH_TIMEOUT: Duration = SetupTurnPolicy.DEADLINE.plusMinutes(1)

    /**
     * Whether a message sent [since] is still being answered: the next one waits for it, so answers
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
        /** A message, which the model answers; after a pick it drops the pick. */
        SAY,

        /** An edition picked from what this session's searches found, its contents held for the reader. */
        PICK,

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
                    Step.CONFIRM -> null
                }
            }

            State.BOOK_CHOSEN -> {
                when (step) {
                    Step.SAY -> State.SEARCHING
                    Step.PICK -> State.BOOK_CHOSEN
                    Step.CONFIRM -> State.CONFIRMED
                }
            }

            State.CONFIRMED -> {
                null
            }
        }
}
