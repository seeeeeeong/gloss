package gloss.generation.domain.job

import java.util.UUID

data class NewJob(
    val accountId: UUID,
    val kind: JobKind,
    val dedupeKey: String,
    val payload: Map<String, String>,
)
