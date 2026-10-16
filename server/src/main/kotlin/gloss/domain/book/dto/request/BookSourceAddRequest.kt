package gloss.domain.book.dto.request

import jakarta.validation.constraints.NotBlank

/** [toc] is the reader's own contents for the edition; without it the source's are used. */
data class BookSourceAddRequest(
    @field:NotBlank val ref: String,
    val toc: String? = null,
)
