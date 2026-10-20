package gloss.domain.post.dto.response

import gloss.domain.post.domain.repository.BookPostCount
import java.util.UUID

/** How many of a book's sections have a post to read; a book with none is left out. */
data class PostCountResponse(
    val bookId: UUID,
    val posts: Long,
) {
    companion object {
        fun from(count: BookPostCount) = PostCountResponse(bookId = count.bookId, posts = count.posts)
    }
}
