package gloss.domain.plan.domain.repository

import gloss.domain.plan.domain.BookPlan
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Every read names the owner, as every read of a reader's own data does. */
interface BookPlanRepository : JpaRepository<BookPlan, UUID> {
    fun existsByTocIdAndAccountId(
        tocId: UUID,
        accountId: UUID,
    ): Boolean

    fun findTopByTocIdAndAccountIdOrderByVersionNoDesc(
        tocId: UUID,
        accountId: UUID,
    ): BookPlan?
}
