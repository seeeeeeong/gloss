package gloss.domain.book.dto.request

import jakarta.validation.constraints.NotBlank

data class TocReviseRequest(
    @field:NotBlank val toc: String,
)
