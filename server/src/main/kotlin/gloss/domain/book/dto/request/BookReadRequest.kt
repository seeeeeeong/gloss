package gloss.domain.book.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BookReadRequest(
    @field:NotBlank @field:Size(max = 600) val itemKey: String,
)
