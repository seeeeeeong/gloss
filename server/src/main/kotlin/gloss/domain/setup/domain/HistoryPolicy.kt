package gloss.domain.setup.domain

/**
 * Which earlier turns of a setup session the model reads again. Past [BUDGET], the oldest go, down to
 * half of it, so the turns kept stay the same, and the prompt's prefix with them, until the budget is
 * reached again: only a message that trims misses the provider's cache.
 */
object HistoryPolicy {
    /**
     * Prompt tokens by the reservation's byte bound, which counts Korean at about three times its real
     * tokens: some 10,000 real ones, about $0.003 a call at the miss price. A turn kept as refs and
     * counts is 1,000 to 2,000, so the last ten or more are read.
     */
    const val BUDGET = 32_000L

    /** The index of the first turn to keep, given each turn's [sizes] in order, oldest first. */
    fun start(sizes: List<Long>): Int {
        var start = 0
        var total = 0L
        sizes.forEach { size ->
            total += size
            if (total > BUDGET) {
                while (start < sizes.size && total > BUDGET / 2) total -= sizes[start++]
            }
        }
        return start
    }
}
