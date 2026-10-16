package gloss.domain.chat.domain

import java.time.Instant
import java.util.UUID

/**
 * One message of a conversation, numbered in order from 1. A reader's keeps the turn exactly as the
 * model was sent it; Gloss's keeps how its answer ended and what the call used.
 */
data class ChatLine(
    val seq: Int,
    val role: ChatRole,
    val itemKey: String?,
    val text: String,
    val createdAt: Instant,
    val asked: Asked? = null,
    val answered: Answered? = null,
) {
    init {
        require((role == ChatRole.READER) == (asked != null) && (role == ChatRole.GLOSS) == (answered != null)) {
            "A reader's message is asked, Gloss's answered"
        }
    }

    /**
     * What a reader's message carried: the page's name for the turn, the phrase they picked, the
     * prompt sent, the post that went in with it, the plan it was asked under and the first turn its
     * history could hold.
     */
    data class Asked(
        val turnId: UUID,
        val quote: String?,
        val prompt: String,
        val postVersionId: UUID?,
        val planId: UUID,
        val historyFrom: Int,
    )

    /** How Gloss's reply to [replyTo] ended. [finishReason] and [errorCode] are this application's codes. */
    data class Answered(
        val replyTo: Int,
        val status: ChatStatus,
        val finishReason: String?,
        val errorCode: String?,
        val model: String,
        val promptVersion: String,
        val usage: ChatUsage?,
        val reservationId: UUID?,
    )
}

/** As the provider reported it, for the call that wrote a reply. */
data class ChatUsage(
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val completionTokens: Long,
    val reasoningTokens: Long?,
)

enum class ChatRole(
    val code: String,
) {
    READER("reader"),
    GLOSS("gloss"),
    ;

    companion object {
        fun of(code: String): ChatRole = entries.first { it.code == code }
    }
}

enum class ChatStatus(
    val code: String,
) {
    /** The model finished its answer. The only kind sent back to it as part of the conversation. */
    DONE("done"),

    /** The reader stopped it, or left. */
    STOPPED("stopped"),

    /** The model ended it for a reason of its own, at its cap or filtered. */
    CUT("cut"),

    /** No answer: the call was refused or failed. */
    FAILED("failed"),
    ;

    companion object {
        fun of(code: String): ChatStatus = entries.first { it.code == code }
    }
}
