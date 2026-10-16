package gloss.domain.setup.dto.request

import jakarta.validation.constraints.NotBlank

/** [revision] is the session's the reader saw; none for the message that starts a session. */
data class SetupMessageRequest(
    @field:NotBlank val text: String,
    val revision: Int? = null,
)
