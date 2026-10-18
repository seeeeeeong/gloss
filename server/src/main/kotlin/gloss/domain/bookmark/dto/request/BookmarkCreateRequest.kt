package gloss.domain.bookmark.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/** [quote] is the phrase picked in the item; without one, or with a blank one, the item itself is kept. */
data class BookmarkCreateRequest(
    val bookId: UUID,
    @field:NotBlank @field:Size(max = 600) val itemKey: String,
    @field:Size(max = 1000) val quote: String? = null,
)
