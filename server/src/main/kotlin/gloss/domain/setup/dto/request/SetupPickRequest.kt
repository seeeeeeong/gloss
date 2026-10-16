package gloss.domain.setup.dto.request

import jakarta.validation.constraints.NotBlank

/** [ref] is an edition a search in the same session found; [revision] the session's the reader saw. */
data class SetupPickRequest(
    @field:NotBlank val ref: String,
    val revision: Int,
)
