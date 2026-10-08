package gloss.generation.domain

import gloss.generation.domain.job.JobKind
import java.util.UUID

data class ClaimedJob(
    val lease: Lease,
    val accountId: UUID,
    val kind: JobKind,
    val maxAttempts: Int,
    val payload: Map<String, String>,
)
