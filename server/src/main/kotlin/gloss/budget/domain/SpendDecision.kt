package gloss.budget.domain

sealed interface SpendDecision {
    data object Allowed : SpendDecision

    data object LimitNotSet : SpendDecision

    data object LimitReached : SpendDecision

    data object TooManyInFlight : SpendDecision

    data object TooFrequent : SpendDecision
}
