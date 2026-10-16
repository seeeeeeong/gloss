package gloss.domain.setup.dto.request

/** [revision] is the session's the reader saw: if it changed since, nothing is made. */
data class SetupConfirmRequest(
    val revision: Int,
)
