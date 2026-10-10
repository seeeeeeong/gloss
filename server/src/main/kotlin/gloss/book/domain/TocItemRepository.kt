package gloss.book.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TocItemRepository : JpaRepository<TocItem, UUID> {
    fun findAllByTocIdAndAccountIdOrderByOrd(
        tocId: UUID,
        accountId: UUID,
    ): List<TocItem>
}
