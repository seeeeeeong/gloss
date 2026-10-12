package gloss.post.application

import gloss.post.domain.PostVersion
import java.util.UUID

/** A finished writing of one section, handed over to be kept as the post's next version. */
data class PublishRequest(
    val accountId: UUID,
    val bookId: UUID,
    val itemKey: String,
    val planId: UUID,
    val contentMd: String,
    val finishReason: String,
    val model: String,
    val promptVersion: String,
    val tokens: PostTokens,
    val jobId: UUID? = null,
)

data class PostTokens(
    val cacheHit: Int,
    val cacheMiss: Int,
    val completion: Int,
)

/** [created] is false when the content was the same as the version before: nothing was added. */
data class PublishedVersion(
    val postId: UUID,
    val versionId: UUID,
    val versionNo: Int,
    val created: Boolean,
) {
    companion object {
        internal fun of(
            version: PostVersion,
            created: Boolean,
        ) = PublishedVersion(
            postId = version.postId,
            versionId = version.id,
            versionNo = version.versionNo,
            created = created,
        )
    }
}
