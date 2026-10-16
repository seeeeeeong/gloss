package gloss.domain.setup.dto.request

import jakarta.validation.constraints.NotBlank

/**
 * Contents the reader typed in: for [ref], an edition this session found without contents, or else
 * for a book of their own named by [title] and [authors]. [revision] is the session's the reader saw,
 * none when the contents start a session.
 */
data class SetupTypedRequest(
    val ref: String? = null,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    @field:NotBlank val toc: String,
    val revision: Int? = null,
)
