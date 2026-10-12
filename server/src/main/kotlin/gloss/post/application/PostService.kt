package gloss.post.application

import gloss.common.error.GlossException
import gloss.post.application.dto.PostResponse
import gloss.post.application.dto.PostSummaryResponse
import gloss.post.domain.Post
import gloss.post.domain.PostContent
import gloss.post.domain.PostRepository
import gloss.post.domain.PostVersion
import gloss.post.domain.PostVersionDraft
import gloss.post.domain.PostVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** A reader's posts: read by the section's key, written one whole version at a time. */
@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val postVersionRepository: PostVersionRepository,
    private val clock: Clock,
) {
    fun list(
        accountId: UUID,
        bookId: UUID,
    ): List<PostSummaryResponse> {
        val posts = postRepository.findAllByBookIdAndAccountIdAndCurrentVersionIdIsNotNull(bookId, accountId)
        val versions =
            postVersionRepository
                .findAllByIdInAndAccountId(posts.mapNotNull { it.currentVersionId }, accountId)
                .associateBy { it.postId }
        return posts.mapNotNull { post -> versions[post.id]?.let { PostSummaryResponse.from(post, it) } }
    }

    fun get(
        accountId: UUID,
        bookId: UUID,
        itemKey: String,
    ): PostResponse {
        val post = postRepository.findByBookIdAndAccountIdAndItemKey(bookId, accountId, itemKey)
        val version = post?.currentVersionId?.let { postVersionRepository.findByIdAndAccountId(it, accountId) }
        if (post == null || version == null) throw GlossException(PostError.POST_NOT_FOUND)
        return PostResponse.from(post, version)
    }

    /**
     * Adds a version and shows it, in one transaction. Content the same as the version before
     * adds nothing. The book and the plan must be the account's own: the schema refuses others.
     */
    @Transactional
    fun publish(request: PublishRequest): PublishedVersion {
        val content = requireNotNull(PostContent.of(request.contentMd)) { "A post's content is out of bounds" }
        require(request.finishReason == PostVersion.FINISHED) { "Only an answer that finished is kept" }
        require(
            request.itemKey.isNotEmpty() &&
                request.itemKey.codePointCount(0, request.itemKey.length) <= Post.MAX_ITEM_KEY,
        ) {
            "An item key is out of bounds"
        }
        postRepository.lockBook(request.bookId)
        val now = clock.instant()
        val post =
            postRepository.findByBookIdAndAccountIdAndItemKey(request.bookId, request.accountId, request.itemKey)
                ?: newPost(request, now)
        val last = postVersionRepository.findTopByPostIdAndAccountIdOrderByVersionNoDesc(post.id, post.accountId)
        if (last != null && last.contentSha256 == content.sha256()) return PublishedVersion.of(last, created = false)
        val draft =
            PostVersionDraft(
                content = content,
                planId = request.planId,
                model = request.model,
                promptVersion = request.promptVersion,
                tokens =
                    PostVersionDraft.Tokens(
                        cacheHit = request.tokens.cacheHit,
                        cacheMiss = request.tokens.cacheMiss,
                        completion = request.tokens.completion,
                    ),
                jobId = request.jobId,
            )
        val version = PostVersion.create(post = post, versionNo = (last?.versionNo ?: 0) + 1, draft = draft, now = now)
        postVersionRepository.save(version)
        post.show(version)
        return PublishedVersion.of(version, created = true)
    }

    private fun newPost(
        request: PublishRequest,
        now: Instant,
    ): Post {
        val post =
            Post.create(
                accountId = request.accountId,
                bookId = request.bookId,
                itemKey = request.itemKey,
                now = now,
            )
        postRepository.save(post)
        return post
    }
}
