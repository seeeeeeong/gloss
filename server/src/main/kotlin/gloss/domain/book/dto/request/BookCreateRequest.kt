package gloss.domain.book.dto.request

import jakarta.validation.constraints.NotBlank

data class BookCreateRequest(
    @field:NotBlank val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val isbn13: String? = null,
    @field:NotBlank val toc: String,
)
