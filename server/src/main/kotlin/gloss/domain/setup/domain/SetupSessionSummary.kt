package gloss.domain.setup.domain

import java.time.Instant
import java.util.UUID

/**
 * A session as the reader's list shows it. [firstText] is the reader's first message, [lastActivityAt]
 * the latest of their steps and Gloss's answers.
 */
data class SetupSessionSummary(
    val id: UUID,
    val state: SetupPolicy.State,
    val heldTitle: String?,
    val firstText: String?,
    val bookId: UUID?,
    val createdAt: Instant,
    val lastActivityAt: Instant,
)
