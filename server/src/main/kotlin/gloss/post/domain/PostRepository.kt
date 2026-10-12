package gloss.post.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** Every read names the owner, as every read of a reader's own data does. */
interface PostRepository : JpaRepository<Post, UUID> {
    fun findByBookIdAndAccountIdAndItemKey(
        bookId: UUID,
        accountId: UUID,
        itemKey: String,
    ): Post?

    fun findAllByBookIdAndAccountIdAndCurrentVersionIdIsNotNull(
        bookId: UUID,
        accountId: UUID,
    ): List<Post>

    // Held until commit, so two writings of one section make the post once and number their versions in turn.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('post:' || cast(:bookId as text), 0))",
        nativeQuery = true,
    )
    fun lockBook(bookId: UUID): Int
}
