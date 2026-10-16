package gloss.domain.post.dto.response

import gloss.domain.post.domain.Post
import gloss.domain.post.domain.PostVersion
import java.time.Instant

/** Which sections of a book have a post to read. */
data class PostSummaryResponse(
    val itemKey: String,
    val versionNo: Int,
    val stale: Boolean,
    val generatedAt: Instant,
) {
    companion object {
        fun from(
            post: Post,
            version: PostVersion,
        ) = PostSummaryResponse(
            itemKey = post.itemKey,
            versionNo = version.versionNo,
            stale = post.stale,
            generatedAt = version.generatedAt,
        )
    }
}
