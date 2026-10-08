package gloss.generation.domain

import java.util.UUID

/** One attempt's hold on a job. Every write it makes names all three, so a lost lease writes nothing. */
data class Lease(
    val jobId: UUID,
    val attempt: Int,
    val owner: String,
)
