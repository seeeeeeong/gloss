package gloss.domain.generation.domain

import java.time.Instant
import java.util.UUID

/**
 * One model call of a job's attempt, as it ended: an answer has its [finishReason], complete or not,
 * and a call without one has [errorCode]. [tokens] is what the provider reported, when it did.
 */
data class GenerationCall(
    val lease: Lease,
    val accountId: UUID,
    val purpose: String,
    val model: String,
    val promptVersion: String,
    val finishReason: String?,
    val errorCode: String?,
    val tokens: CallTokens?,
    val reservationId: UUID?,
    val occurredAt: Instant,
)

data class CallTokens(
    val cacheHit: Long,
    val cacheMiss: Long,
    val completion: Long,
    val reasoning: Long?,
)
