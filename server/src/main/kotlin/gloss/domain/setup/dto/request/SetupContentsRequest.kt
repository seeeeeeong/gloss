package gloss.domain.setup.dto.request

import jakarta.validation.constraints.NotBlank

/** [revision] is the session's the reader saw: if it changed since, nothing is saved. */
data class SetupContentsRequest(
    @field:NotBlank val toc: String,
    val revision: Int,
)
