package gloss.domain.chat.domain

import java.util.UUID

/**
 * Which finished turns the model is sent again with a new one. Only a turn Gloss finished goes back,
 * each exactly as first sent, and only turns asked under the plan in use. The turns go back from a
 * start each turn keeps; while they fit [HISTORY_TOKENS] the start stays, and when they outgrow it
 * the oldest are let go down to half of it at once, so the start moves seldom and the cache misses
 * only on that turn.
 */
object ChatHistoryPolicy {
    /**
     * Counted as [tokensOf] counts, the bound the reservation uses, so about a third of that in real
     * tokens for Korean: some 16,000 tokens of history, $0.0001 a turn from the cache and $0.005 on the
     * turn that moves the start, at deepseek-flash's peak prices.
     */
    const val HISTORY_TOKENS = 48_000L

    // The role and separator tokens around each message, as PromptSizePolicy holds them.
    private const val FRAMING_PER_MESSAGE = 8L

    /** A finished turn: [seq] is the reader's message, its prompt as sent and Gloss's reply as answered. */
    data class Exchange(
        val seq: Int,
        val prompt: String,
        val reply: String,
        val postVersionId: UUID? = null,
    ) {
        val tokens: Long get() = tokensOf(prompt) + tokensOf(reply) + 2 * FRAMING_PER_MESSAGE
    }

    /** The turns to send again, and the start the new turn keeps for the next. */
    data class Window(
        val from: Int,
        val exchanges: List<Exchange>,
    ) {
        /** Whether [postVersionId] has still to go in: no turn sent again carried it. */
        fun sendsPost(postVersionId: UUID): Boolean = exchanges.none { it.postVersionId == postVersionId }
    }

    /** Where the conversation's last turn stood: its start, and the plan it was asked under. */
    data class Last(
        val from: Int,
        val planId: UUID,
    )

    /**
     * [finished] are the finished turns from the last turn's start, oldest first, and [next] the new
     * turn's number. A plan other than the last turn's starts the history afresh at the new turn.
     */
    fun window(
        last: Last?,
        planId: UUID,
        finished: List<Exchange>,
        next: Int,
        budget: Long = HISTORY_TOKENS,
    ): Window {
        if (last == null || last.planId != planId) return Window(from = next, exchanges = emptyList())
        val kept = ArrayDeque(finished.filter { it.seq >= last.from }.sortedBy { it.seq })
        var total = kept.sumOf { it.tokens }
        // Turns that never finished hold no place: the start moves up to the first that did, or past them all.
        if (total <= budget) return Window(from = kept.firstOrNull()?.seq ?: next, exchanges = kept.toList())
        while (kept.isNotEmpty() && total > budget / 2) total -= kept.removeFirst().tokens
        return Window(from = kept.firstOrNull()?.seq ?: next, exchanges = kept.toList())
    }

    /** At most the tokens [text] can bill: a byte-level token covers a byte at least. */
    fun tokensOf(text: String): Long = text.toByteArray(Charsets.UTF_8).size.toLong()
}
