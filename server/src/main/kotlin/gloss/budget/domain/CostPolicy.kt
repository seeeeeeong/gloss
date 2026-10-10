package gloss.budget.domain

/** Money is µUSD, a Long; rates are µUSD per million tokens, as providers price them. */
object CostPolicy {
    private const val PER_RATE = 1_000_000L

    data class Rates(
        val hit: Long,
        val miss: Long,
        val out: Long,
    )

    data class Tokens(
        val hit: Long,
        val miss: Long,
        val out: Long,
    )

    // Rounded up once per row: a cache hit costs less than a micro-dollar a token, so rounding
    // each token would overcharge and truncating would let hits run free.
    fun usageMicros(
        tokens: Tokens,
        rates: Rates,
    ): Long {
        val scaled =
            Math.addExact(
                Math.addExact(Math.multiplyExact(tokens.hit, rates.hit), Math.multiplyExact(tokens.miss, rates.miss)),
                Math.multiplyExact(tokens.out, rates.out),
            )
        return Math.ceilDiv(scaled, PER_RATE)
    }

    /**
     * The most a call can cost: every prompt token missing the cache, every allowed output token
     * spent. At least one, so a free model or a zero cap still leaves a row the caps can count.
     */
    fun reserveMicros(
        promptTokens: Long,
        maxTokens: Long,
        rates: Rates,
    ): Long = usageMicros(Tokens(hit = 0, miss = promptTokens, out = maxTokens), rates).coerceAtLeast(1)
}
