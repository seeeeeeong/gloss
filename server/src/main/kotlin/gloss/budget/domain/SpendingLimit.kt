package gloss.budget.domain

import gloss.common.entity.BaseEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import java.time.Instant
import java.util.UUID

/** A reader's monthly limit, keyed by their account id. */
@Entity
@AttributeOverride(name = "id", column = Column(name = "account_id"))
class SpendingLimit private constructor(
    accountId: UUID,
    monthlyLimitMicros: Long,
    now: Instant,
) : BaseEntity(accountId) {
    var monthlyLimitMicros: Long = monthlyLimitMicros
        protected set
    var updatedAt: Instant = now
        protected set

    fun change(
        monthlyLimitMicros: Long,
        now: Instant,
    ) {
        this.monthlyLimitMicros = monthlyLimitMicros
        updatedAt = now
    }

    companion object {
        fun create(
            accountId: UUID,
            monthlyLimitMicros: Long,
            now: Instant,
        ) = SpendingLimit(accountId, monthlyLimitMicros, now)
    }
}
