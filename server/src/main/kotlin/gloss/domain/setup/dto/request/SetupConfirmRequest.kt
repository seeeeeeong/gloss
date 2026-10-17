package gloss.domain.setup.dto.request

/**
 * [revision] is the session's the reader saw: if it changed since, nothing is made. [level] and
 * [voice] are the codes of how the book's posts are written.
 */
data class SetupConfirmRequest(
    val revision: Int,
    val level: String,
    val voice: String,
)
