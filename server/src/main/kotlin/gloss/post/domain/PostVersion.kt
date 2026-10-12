package gloss.post.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

/** One writing of a post, whole. Append-only: a rewrite is a new version. */
@Entity
@Immutable
class PostVersion private constructor(
    id: UUID,
    post: Post,
    versionNo: Int,
    draft: PostVersionDraft,
    now: Instant,
) : BaseEntity(id) {
    val accountId: UUID = post.accountId
    val bookId: UUID = post.bookId
    val postId: UUID = post.id
    val versionNo: Int = versionNo
    val planId: UUID = draft.planId
    val contentMd: String = draft.content.value
    val contentSha256: String = draft.content.sha256()
    val model: String = draft.model
    val promptVersion: String = draft.promptVersion
    val finishReason: String = FINISHED
    val cacheHitTokens: Int = draft.tokens.cacheHit
    val cacheMissTokens: Int = draft.tokens.cacheMiss
    val completionTokens: Int = draft.tokens.completion
    val jobId: UUID? = draft.jobId
    val generatedAt: Instant = now

    companion object {
        // The only finish reason of an answer that is whole; a cut-off one is never kept.
        const val FINISHED = "stop"

        fun create(
            post: Post,
            versionNo: Int,
            draft: PostVersionDraft,
            now: Instant,
        ) = PostVersion(id = UuidV7.at(now), post = post, versionNo = versionNo, draft = draft, now = now)
    }
}

/** What a finished generation hands over, its content already checked. */
data class PostVersionDraft(
    val content: PostContent,
    val planId: UUID,
    val model: String,
    val promptVersion: String,
    val tokens: Tokens,
    val jobId: UUID?,
) {
    data class Tokens(
        val cacheHit: Int,
        val cacheMiss: Int,
        val completion: Int,
    )
}
