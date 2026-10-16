package gloss.domain.setup.dto.response

import gloss.domain.setup.domain.SetupMessage
import gloss.domain.setup.domain.SetupSession
import java.time.Instant
import java.util.UUID

/**
 * [held] is there while a picked edition waits for the reader to start; [bookId] once they have.
 * [searching] while the latest message is still being answered. Every change bumps
 * [revision], and every request that changes the session names the one it saw.
 */
data class SetupSessionResponse(
    val id: UUID,
    val revision: Int,
    val state: String,
    val searching: Boolean,
    val bookId: UUID?,
    val held: SetupContentsResponse?,
    val messages: List<SetupMessageResponse>,
) {
    companion object {
        fun from(
            session: SetupSession,
            messages: List<SetupMessage>,
            held: SetupContentsResponse?,
            now: Instant,
        ) = SetupSessionResponse(
            id = session.id,
            revision = session.revision,
            state = session.state.code,
            searching = session.searching(now),
            bookId = session.bookId,
            held = held,
            messages = messages.map(SetupMessageResponse::from),
        )
    }
}
