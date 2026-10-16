package gloss.domain.post.service

import gloss.common.util.traceOf
import gloss.domain.generation.service.GenerationService
import gloss.domain.generation.service.RunningJob
import gloss.domain.post.domain.CheckedWriting
import gloss.domain.post.domain.PostCheck
import gloss.domain.post.domain.WritingClaim
import gloss.domain.post.infrastructure.CommonMarkOutline
import gloss.domain.post.infrastructure.JdbcPostChecks
import gloss.domain.post.infrastructure.JdbcPostWritings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * A post's writings: each taken before its call, so a job writes twice at most however its attempts
 * race, and each judged by the outline the page would show, every judgement kept.
 */
@Service
class PostCheckService(
    private val commonMarkOutline: CommonMarkOutline,
    private val jdbcPostChecks: JdbcPostChecks,
    private val jdbcPostWritings: JdbcPostWritings,
    private val generationService: GenerationService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** How many writings the job has taken, over all its attempts, called or not. */
    fun taken(job: RunningJob): Int = jdbcPostWritings.taken(job.accountId, job.jobId)

    /** Takes the job's [writing] for this attempt; false when another took it, or the attempt lost the job. */
    @Transactional
    fun take(
        job: RunningJob,
        writing: Int,
    ): Boolean {
        if (!generationService.holdLease(job)) return false
        val claim =
            WritingClaim(
                accountId = job.accountId,
                jobId = job.jobId,
                writing = writing,
                attempt = job.attempt,
                claimedAt = clock.instant(),
            )
        return jdbcPostWritings.take(claim)
    }

    fun judge(writing: PostWriting): PostCheck.Verdict {
        val verdict =
            PostCheck.judge(
                finishReason = writing.finishReason,
                text = writing.text,
                outline = commonMarkOutline.of(writing.text),
                form = writing.form,
            )
        record(
            CheckedWriting(
                jobId = writing.jobId,
                attempt = writing.attempt,
                writing = writing.writing,
                accountId = writing.accountId,
                bookId = writing.bookId,
                planId = writing.planId,
                itemKey = writing.itemKey,
                promptVersion = writing.promptVersion,
                finishReason = writing.finishReason,
                verdict = verdict,
                checkedAt = clock.instant(),
            ),
        )
        return verdict
    }

    // A side record, as a job's calls are: failing to write it never takes back a writing paid for.
    @Suppress("TooGenericExceptionCaught")
    private fun record(checked: CheckedWriting) {
        try {
            jdbcPostChecks.append(checked)
        } catch (e: RuntimeException) {
            log.warn("Could not record a check of job {}{}", checked.jobId, traceOf(e))
        }
    }
}

/** One writing of a section's post: the [writing]th of its job, made in the job's [attempt]. */
data class PostWriting(
    val jobId: UUID,
    val attempt: Int,
    val writing: Int,
    val accountId: UUID,
    val bookId: UUID,
    val planId: UUID,
    val itemKey: String,
    val promptVersion: String,
    val form: PostCheck.Form,
    val finishReason: String,
    val text: String,
)
