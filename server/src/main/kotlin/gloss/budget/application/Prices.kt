package gloss.budget.application

/** µUSD per million tokens, as the provider documents them for the model in use. */
data class Prices(
    val hitPerMillion: Long,
    val missPerMillion: Long,
    val outPerMillion: Long,
)
