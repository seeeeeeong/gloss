package gloss.domain.llm.service

import gloss.domain.budget.service.Prices

/**
 * The model every call uses, as api-docs.deepseek.com lists it (pricing page, read 2026-10-07):
 * `deepseek-flash`, 1M context, output up to 384K. Per million tokens at peak: input $0.006 on a
 * cache hit, $0.30 on a miss; output $1.20. Off-peak is half, but the ledger always prices at
 * peak, so an estimate is never below the bill. Changing any of this is its own pull request
 * that states the cost.
 */
object DeepSeekModel {
    const val ID = "deepseek-flash"

    /** µUSD per million tokens, peak. */
    val PRICES = Prices(hitPerMillion = 6_000, missPerMillion = 300_000, outPerMillion = 1_200_000)

    /** 384K, the documented ceiling for max_tokens. */
    const val MAX_OUTPUT_TOKENS = 393_216

    /** "1M" as documented, read as the lower 1,000,000; prompt and output together fit in it. */
    const val CONTEXT_TOKENS = 1_000_000
}
