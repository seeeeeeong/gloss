package gloss.post.application.dto

import gloss.post.domain.Post
import gloss.post.domain.PostVersion
import java.time.Instant
import java.util.UUID

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
