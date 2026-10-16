package gloss.domain.post.dto.response

import gloss.domain.post.domain.Post
import gloss.domain.post.domain.PostVersion
import java.time.Instant
import java.util.UUID

/** A post as a reader sees it: its current version. */
data class PostResponse(
    val id: UUID,
    val itemKey: String,
    val versionId: UUID,
    val versionNo: Int,
    val stale: Boolean,
    val contentMd: String,
    val model: String,
    val generatedAt: Instant,
) {
    companion object {
        fun from(
            post: Post,
            version: PostVersion,
        ) = PostResponse(
            id = post.id,
            itemKey = post.itemKey,
            versionId = version.id,
            versionNo = version.versionNo,
            stale = post.stale,
            contentMd = version.contentMd,
            model = version.model,
            generatedAt = version.generatedAt,
        )
    }
}
