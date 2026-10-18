package gloss.domain.bookmark.domain.repository

import gloss.domain.bookmark.domain.Bookmark
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BookmarkRepository : JpaRepository<Bookmark, UUID> {
    fun findAllByAccountIdOrderByCreatedAtDescIdDesc(accountId: UUID): List<Bookmark>

    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): Bookmark?

    // Matched as bookmark_once matches, so a bookmark of the item itself, with no quote, is found too.
    @Query(
        "select b from Bookmark b where b.accountId = :accountId and b.bookId = :bookId" +
            " and b.itemKey = :itemKey and coalesce(b.quote, '') = coalesce(:quote, '')",
    )
    fun findSame(
        accountId: UUID,
        bookId: UUID,
        itemKey: String,
        quote: String?,
    ): Bookmark?

    // Held until commit, so the same phrase saved twice at once is kept once.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('bookmark:' || cast(:bookId as text), 0))",
        nativeQuery = true,
    )
    fun lockBook(bookId: UUID): Int
}
