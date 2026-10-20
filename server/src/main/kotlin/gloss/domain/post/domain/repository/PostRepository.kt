package gloss.domain.post.domain.repository

import gloss.domain.post.domain.Post
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

    @Query(
        "select p.bookId as bookId, count(p) as posts from Post p " +
            "where p.accountId = :accountId and p.currentVersionId is not null group by p.bookId",
    )
    fun countWrittenByBook(accountId: UUID): List<BookPostCount>

    // Held until commit, so two writings of one section make the post once and number their versions in turn.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('post:' || cast(:bookId as text), 0))",
        nativeQuery = true,
    )
    fun lockBook(bookId: UUID): Int
}

/** How many of a book's sections have a post to read. */
interface BookPostCount {
    val bookId: UUID
    val posts: Long
}
