package gloss.budget.application

import java.util.UUID

/** Handed back to settle or release it; the account is checked against the ledger's row. */
data class Reservation(
    val id: UUID,
    val accountId: UUID,
    val amountMicros: Long,
)
