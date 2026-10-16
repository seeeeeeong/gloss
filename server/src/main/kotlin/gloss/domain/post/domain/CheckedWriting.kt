package gloss.domain.post.domain

import java.time.Instant
import java.util.UUID

/** One writing of a section's post as [PostCheck] judged it, kept whether it passed or not. */
data class CheckedWriting(
    val jobId: UUID,
    val attempt: Int,
    val writing: Int,
    val accountId: UUID,
    val bookId: UUID,
    val planId: UUID,
    val itemKey: String,
    val promptVersion: String,
    val finishReason: String,
    val verdict: PostCheck.Verdict,
    val checkedAt: Instant,
)
