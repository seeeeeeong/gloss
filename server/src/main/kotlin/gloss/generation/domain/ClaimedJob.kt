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

/** One attempt's hold on a job. Every write it makes names all three, so a lost lease writes nothing. */
data class Lease(
    val jobId: UUID,
    val attempt: Int,
    val owner: String,
)
