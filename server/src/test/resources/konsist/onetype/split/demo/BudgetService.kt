package demo

class BudgetService {
    fun reserve(amountMicros: Long) = Reservation(amountMicros)

    private companion object {
        /** Nested, so it belongs to the service's file. */
        const val LIMIT = 5_000_000L
    }
}

fun unlimited() = BudgetService()
