package gloss.domain.setup.domain

import gloss.domain.setup.domain.SetupPolicy.State
import gloss.domain.setup.domain.SetupPolicy.Step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SetupPolicyTest {
    @Test
    fun `a reader searches as often as they like and picks from what was found`() {
        assertThat(SetupPolicy.next(State.SEARCHING, Step.SAY)).isEqualTo(State.SEARCHING)
        assertThat(SetupPolicy.next(State.SEARCHING, Step.PICK)).isEqualTo(State.BOOK_CHOSEN)
    }

    @Test
    fun `nothing is edited or started before a book is picked`() {
        assertThat(SetupPolicy.next(State.SEARCHING, Step.EDIT)).isNull()
        assertThat(SetupPolicy.next(State.SEARCHING, Step.CONFIRM)).isNull()
    }

    @Test
    fun `with a book chosen, a message searches again, a pick replaces it, and an edit keeps it`() {
        assertThat(SetupPolicy.next(State.BOOK_CHOSEN, Step.SAY)).isEqualTo(State.SEARCHING)
        assertThat(SetupPolicy.next(State.BOOK_CHOSEN, Step.PICK)).isEqualTo(State.BOOK_CHOSEN)
        assertThat(SetupPolicy.next(State.BOOK_CHOSEN, Step.EDIT)).isEqualTo(State.BOOK_CHOSEN)
    }

    @Test
    fun `starting with the chosen book's contents ends the session`() {
        assertThat(SetupPolicy.next(State.BOOK_CHOSEN, Step.CONFIRM)).isEqualTo(State.CONFIRMED)
        assertThat(Step.entries.map { SetupPolicy.next(State.CONFIRMED, it) }).containsOnlyNulls()
    }

    @Test
    fun `a search holds the next message back until it is answered or its time has passed`() {
        val asked = Instant.parse("2026-10-17T00:00:00Z")

        assertThat(SetupPolicy.searching(null, asked)).isFalse()
        assertThat(SetupPolicy.searching(asked, asked.plusSeconds(29))).isTrue()
        assertThat(SetupPolicy.searching(asked, asked.plusSeconds(30))).isFalse()
    }
}
