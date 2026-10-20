package gloss.domain.post.service

import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.service.TocService
import gloss.domain.budget.service.BudgetError
import gloss.domain.generation.domain.job.JobState
import gloss.domain.generation.domain.job.JobStatus
import gloss.domain.generation.service.GenerationService
import gloss.domain.llm.service.LlmError
import gloss.domain.plan.service.PlanItemKind
import gloss.domain.plan.service.PlanService
import gloss.domain.post.dto.response.PostGenerationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A reader opening a section asks for its post here, and asks again to see how far it has come.
 * The book's plan is written first if it has none; the post follows once the plan is kept. A post
 * the book's newer plan has made stale is written again when the reader asks with a retry.
 */
@Service
class PostGenerationService(
    private val tocService: TocService,
    private val planService: PlanService,
    private val postService: PostService,
    private val generationService: GenerationService,
) {
    /**
     * Asking again while the work waits or runs adds nothing. Work that failed is asked for again
     * only on [retry]: a page that asks as it opens must not spend the reader's key on what just
     * failed.
     */
    @Transactional
    fun request(
        accountId: UUID,
        bookId: UUID,
        itemKey: String,
        retry: Boolean,
    ): PostGenerationResponse {
        val outline = tocService.outline(accountId, bookId)
        val item =
            outline?.items?.firstOrNull { it.itemKey == itemKey }
                ?: throw GlossException(PostError.SECTION_NOT_FOUND)
        // Front and back matter is never read, so it is never written, even under a plan that has it.
        if (item !in outline.readableItems) return PostGenerationResponse.NONE
        val freshness = postService.freshness(accountId, bookId, itemKey)
        if (freshness == PostFreshness.FRESH) return PostGenerationResponse.READY
        val plan = planService.find(accountId, outline.tocId)
        val work =
            when {
                plan == null -> {
                    Work(PostGenerationResponse.PLANNING, planService.progress(accountId, outline.tocId)) {
                        planService.request(accountId = accountId, bookId = bookId, tocId = outline.tocId)
                    }
                }

                plan.kinds[item.ord] in NOTHING_TO_WRITE -> {
                    return PostGenerationResponse.NONE
                }

                else -> {
                    val job = PostJobHandler.jobFor(accountId = accountId, plan = plan, itemKey = itemKey)
                    Work(PostGenerationResponse.WRITING, generationService.latest(accountId, job.dedupeKey)) {
                        generationService.enqueue(job)
                    }
                }
            }
        // A post of a plan since replaced stays what the reader sees; it is rewritten when they ask.
        val keep = freshness == PostFreshness.STALE && !retry && work.idle
        return if (keep) PostGenerationResponse.READY else work.asked(retry)
    }

    /**
     * Marks the book's posts of earlier plans stale, now that [planId] is kept. A plan no longer the
     * book's own marks nothing: its event came late, after a newer plan and posts written from it.
     */
    @Transactional
    fun outdatePosts(
        accountId: UUID,
        bookId: UUID,
        planId: UUID,
    ) {
        // Held, so no newer plan is kept between the look and the mark.
        val tocId = tocService.holdCurrentTocId(accountId, bookId) ?: return
        if (planService.find(accountId, tocId)?.id != planId) return
        postService.outdate(accountId = accountId, bookId = bookId, planId = planId)
    }

    /** One step toward the post: what it shows as while it runs, how its job stands, and how to ask for it. */
    private class Work(
        val running: PostGenerationResponse,
        val status: JobStatus?,
        val ask: () -> Unit,
    ) {
        /** Nothing waits, runs or has failed: the work was never asked for, or ended without a word to say. */
        val idle: Boolean get() = status == null || (!status.state.live && status.state != JobState.FAILED)

        fun asked(retry: Boolean): PostGenerationResponse {
            val failed = status?.takeIf { it.state == JobState.FAILED && !retry }
            if (failed != null) {
                val code = failed.errorKind ?: UNKNOWN
                return PostGenerationResponse.failed(code = code, message = messageOf(code))
            }
            if (status?.state?.live != true) ask()
            return running
        }
    }

    private companion object {
        // An item the plan leaves out has no kind: it has nothing to write either.
        val NOTHING_TO_WRITE = setOf(PlanItemKind.NONE, null)
        const val UNKNOWN = "error"
        const val FAILED = "글을 만들지 못했습니다. 다시 시도해 주세요"

        // A job keeps only the code of what stopped it; the reader is told in that error's own words.
        val KNOWN: List<ErrorCode> = LlmError.entries + BudgetError.entries
        val OWN =
            mapOf(
                "plan.too_many_items" to "목차 항목이 너무 많아 글을 만들 수 없습니다. 목차를 줄여 다시 확정해 주세요",
            )

        fun messageOf(code: String): String = KNOWN.firstOrNull { it.code == code }?.message ?: OWN[code] ?: FAILED
    }
}
