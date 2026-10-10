package demo

object SpendPolicy {
    fun decide(spent: Long): SpendDecision = if (spent > LIMIT) SpendDecision.Refused else SpendDecision.Allowed

    private const val LIMIT = 5_000_000L
}

sealed interface SpendDecision {
    data object Allowed : SpendDecision

    data object Refused : SpendDecision
}
