package gloss.domain.book.domain.repository

import gloss.domain.book.domain.TocItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TocItemRepository : JpaRepository<TocItem, UUID> {
    fun findAllByTocIdAndAccountIdOrderByOrd(
        tocId: UUID,
        accountId: UUID,
    ): List<TocItem>

    fun countByTocIdAndAccountId(
        tocId: UUID,
        accountId: UUID,
    ): Long
}
