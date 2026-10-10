package gloss.book.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BookRepository : JpaRepository<Book, UUID> {
    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): Book?

    fun findAllByAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(accountId: UUID): List<Book>

    fun existsByAccountIdAndIsbn13AndDeletedAtIsNull(
        accountId: UUID,
        isbn13: String,
    ): Boolean

    // Held until commit, so two adds to one shelf check for the same isbn in turn.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('book:' || cast(:accountId as text), 0))",
        nativeQuery = true,
    )
    fun lockShelf(accountId: UUID): Int

    // Held until commit, so two edits of the same contents number their versions in turn.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id and b.accountId = :accountId and b.deletedAt is null")
    fun findForUpdate(
        id: UUID,
        accountId: UUID,
    ): Book?
}
