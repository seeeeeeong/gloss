package gloss.domain.book.domain.repository

import gloss.domain.book.domain.Toc
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** Every read names the owner, as every read of a reader's own data does. */
interface TocRepository : JpaRepository<Toc, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): Toc?

    fun findTopByBookIdAndAccountIdOrderByVersionNoDesc(
        bookId: UUID,
        accountId: UUID,
    ): Toc?

    fun existsByIdAndBookIdAndAccountId(
        id: UUID,
        bookId: UUID,
        accountId: UUID,
    ): Boolean

    @Query("select coalesce(max(t.versionNo), 0) from Toc t where t.bookId = :bookId and t.accountId = :accountId")
    fun lastVersionNo(
        bookId: UUID,
        accountId: UUID,
    ): Int
}
