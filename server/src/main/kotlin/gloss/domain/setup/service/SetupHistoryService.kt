package gloss.domain.setup.service

import gloss.domain.setup.dto.response.SetupSessionSummaryResponse
import gloss.domain.setup.infrastructure.JdbcSetupSessions
import org.springframework.stereotype.Service
import java.util.UUID

/** A reader's earlier setup sessions, to go back to. */
@Service
class SetupHistoryService(
    private val jdbcSetupSessions: JdbcSetupSessions,
) {
    /**
     * The reader's own sessions, latest activity first; one they opened and never wrote in is not
     * listed. Only those that made [bookId] when it is named, so an old book still finds its own.
     */
    fun list(
        accountId: UUID,
        bookId: UUID?,
    ): List<SetupSessionSummaryResponse> {
        val sessions = jdbcSetupSessions.recent(accountId = accountId, bookId = bookId, limit = LISTED)
        return sessions.map(SetupSessionSummaryResponse::from)
    }

    private companion object {
        // A sidebar's worth; an older session is reached from the book it made.
        const val LISTED = 50
    }
}
