package demo

data class Reservation(
    val amountMicros: Long,
)

class BudgetService {
    fun reserve(amountMicros: Long) = Reservation(amountMicros)
}
