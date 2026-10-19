package gloss.domain.chat.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * A reader's question, from [itemKey] when they were on an item, about [quote] when they picked one.
 * [turnId] is the page's own name for the turn, which a stop sent before any answer names, and
 * [postVersionId] the version of the item's post the reader was shown, which the question is about.
 * [sessionId] is the conversation it goes on; without one, the question starts a new conversation.
 */
data class ChatAskRequest(
    // A question of a few sentences: at most about 6,000 prompt tokens, $0.002 at the miss price. A
    // passage to ask about goes in the quote, which the book's own text bounds.
    @field:NotBlank @field:Size(max = 2000) val text: String,
    @field:Size(min = 1, max = 600) val itemKey: String? = null,
    // The hard cap for any caller, a short paragraph of about 3,000 prompt tokens at most; the page
    // trims a pick to 280 sooner, since the post itself already goes in with the turn.
    @field:Size(max = 1000) val quote: String? = null,
    val turnId: UUID? = null,
    val postVersionId: UUID? = null,
    val sessionId: UUID? = null,
)
