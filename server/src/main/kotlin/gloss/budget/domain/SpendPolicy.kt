package gloss.budget.domain

object SpendPolicy {
    /** What the ledger says right now, read under the account's lock. */
    data class Spend(
        val limit: Long?,
        val spent: Long,
        val inFlight: Int,
        val lastMinute: Int,
        val lastHour: Int,
    )

    data class Caps(
        val inFlight: Int,
        val perMinute: Int,
        val perHour: Int,
    )

    fun decide(
        spend: Spend,
        amount: Long,
        caps: Caps,
    ): SpendDecision =
        when {
            spend.inFlight >= caps.inFlight -> SpendDecision.TooManyInFlight
            spend.lastMinute >= caps.perMinute || spend.lastHour >= caps.perHour -> SpendDecision.TooFrequent
            spend.limit == null -> SpendDecision.LimitNotSet
            spend.spent + amount > spend.limit -> SpendDecision.LimitReached
            else -> SpendDecision.Allowed
        }
}

sealed interface SpendDecision {
    data object Allowed : SpendDecision

    data object LimitNotSet : SpendDecision

    data object LimitReached : SpendDecision

    data object TooManyInFlight : SpendDecision

    data object TooFrequent : SpendDecision
}
