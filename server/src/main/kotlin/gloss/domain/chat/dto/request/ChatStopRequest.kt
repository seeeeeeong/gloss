package gloss.domain.chat.dto.request

import jakarta.validation.constraints.NotNull
import java.util.UUID

/** The turn to stop, by the id the page gave it when it asked, so a stop that arrives first still counts. */
data class ChatStopRequest(
    @field:NotNull val turnId: UUID?,
)
