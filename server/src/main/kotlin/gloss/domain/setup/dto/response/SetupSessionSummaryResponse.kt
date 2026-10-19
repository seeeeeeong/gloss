package gloss.domain.setup.dto.response

import gloss.domain.setup.domain.SetupSessionSummary
import gloss.domain.setup.domain.SetupTitlePolicy
import java.time.Instant
import java.util.UUID

/** One of the reader's sessions in their list; [bookId] once they started with the book it made. */
data class SetupSessionSummaryResponse(
    val id: UUID,
    val state: String,
    val title: String,
    val bookId: UUID?,
    val createdAt: Instant,
    val lastActivityAt: Instant,
) {
    companion object {
        fun from(summary: SetupSessionSummary) =
            SetupSessionSummaryResponse(
                id = summary.id,
                state = summary.state.code,
                title = SetupTitlePolicy.of(summary.heldTitle, summary.firstText),
                bookId = summary.bookId,
                createdAt = summary.createdAt,
                lastActivityAt = summary.lastActivityAt,
            )
    }
}
