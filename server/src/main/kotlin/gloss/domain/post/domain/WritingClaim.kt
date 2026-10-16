package gloss.domain.post.domain

import java.time.Instant
import java.util.UUID

/** A job's [writing] of its post, taken by its [attempt] before the model is called. */
data class WritingClaim(
    val accountId: UUID,
    val jobId: UUID,
    val writing: Int,
    val attempt: Int,
    val claimedAt: Instant,
)
