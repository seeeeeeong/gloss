package gloss.domain.setup.dto.request

/** [revision] is the session's the reader saw: if it changed since, nothing is looked for. */
data class SetupSeekRequest(
    val revision: Int,
)
