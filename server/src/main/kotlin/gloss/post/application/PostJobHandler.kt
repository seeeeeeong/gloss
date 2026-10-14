package gloss.post.application

import gloss.book.application.OutlineItem
import gloss.book.application.TocService
import gloss.budget.application.Purpose
import gloss.generation.application.JobCallService
import gloss.generation.application.JobFailed
import gloss.generation.application.JobHandler
import gloss.generation.application.RunningJob
import gloss.generation.domain.job.JobKind
import gloss.generation.domain.job.NewJob
import gloss.llm.application.LlmCall
import gloss.llm.application.LlmReply
import gloss.llm.domain.chat.ChatMessage
import gloss.llm.domain.chat.Role
import gloss.llm.domain.chat.Thinking
import gloss.plan.application.PlanItemKind
import gloss.plan.application.PlanService
import gloss.plan.application.PlanView
import gloss.post.domain.PostContent
import gloss.post.domain.PostPrompt
import gloss.post.domain.PostVersion
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Writes the post of one section in one model call, from the plan of the contents in use. Only an
 * answer that finished is kept, and only while those contents are still the ones in use.
 */
@Component
class PostJobHandler(
    private val tocService: TocService,
    private val planService: PlanService,
    private val postService: PostService,
    private val jobCallService: JobCallService,
) : JobHandler {
    override val kind = JobKind.POST

    override fun handle(job: RunningJob): () -> Unit {
        val bookId = UUID.fromString(job.payload.getValue(BOOK_ID))
        val itemKey = job.payload.getValue(ITEM_KEY)
        if (postService.freshness(job.accountId, bookId, itemKey) == PostFreshness.FRESH) return {}
        val section = sectionOf(job.accountId, Asked(bookId, UUID.fromString(job.payload.getValue(PLAN_ID)), itemKey))
        val reply = jobCallService.complete(job, callFor(job.accountId, section), PostPrompt.VERSION)
        if (PostContent.of(reply.text) == null) throw JobFailed(EMPTY_REPLY, retryable = false)
        val written = writingOf(job, section, reply)
        return {
            // Confirmed anew while it was written: the post would be of contents no longer in use.
            if (tocService.holdCurrentTocId(job.accountId, bookId) == section.plan.tocId) postService.publish(written)
        }
    }

    private class Section(
        val bookId: UUID,
        val item: OutlineItem,
        val plan: PlanView,
    )

    private class Asked(
        val bookId: UUID,
        val planId: UUID,
        val itemKey: String,
    )

    private fun sectionOf(
        accountId: UUID,
        asked: Asked,
    ): Section {
        val outline = tocService.outline(accountId, asked.bookId)
        // Gone from the contents, or the book from the shelf, since the reader asked.
        val item = outline?.items?.firstOrNull { it.itemKey == asked.itemKey } ?: throw JobFailed(SECTION_GONE, false)
        // The plan the reader asked under, or none: contents confirmed since have a plan and a job of their own.
        val plan =
            planService.find(accountId, outline.tocId)?.takeIf { it.id == asked.planId }
                ?: throw JobFailed(PLAN_GONE, retryable = false)
        return Section(bookId = asked.bookId, item = item, plan = plan).also(::requireSomethingToWrite)
    }

    private fun requireSomethingToWrite(section: Section) {
        val nothing = section.plan.kinds.getOrNull(section.item.ord) == PlanItemKind.NONE
        if (nothing) throw JobFailed(NOTHING_TO_WRITE, retryable = false)
    }

    // Thinking is off: it bills its reasoning as output, and the plan has already drawn the post's bounds.
    private fun callFor(
        accountId: UUID,
        section: Section,
    ): LlmCall {
        val target = PostPrompt.Target(ord = section.item.ord, number = section.item.number, title = section.item.title)
        return LlmCall(
            accountId = accountId,
            purpose = Purpose.POST,
            messages =
                listOf(
                    ChatMessage(Role.SYSTEM, PostPrompt.SYSTEM),
                    ChatMessage(Role.USER, PostPrompt.user(section.plan.canonicalJson, target)),
                ),
            maxTokens = PostPrompt.MAX_TOKENS,
            thinking = Thinking.DISABLED,
        )
    }

    private fun writingOf(
        job: RunningJob,
        section: Section,
        reply: LlmReply,
    ) = PublishRequest(
        accountId = job.accountId,
        bookId = section.bookId,
        itemKey = section.item.itemKey,
        planId = section.plan.id,
        contentMd = reply.text,
        finishReason = PostVersion.FINISHED,
        model = reply.model,
        promptVersion = PostPrompt.VERSION,
        tokens =
            PostTokens(
                cacheHit = tokens(reply.usage?.cacheHitTokens),
                cacheMiss = tokens(reply.usage?.cacheMissTokens),
                completion = tokens(reply.usage?.completionTokens),
            ),
        jobId = job.jobId,
    )

    // Zero when the provider never reported usage: the ledger then holds the estimate it settled at.
    private fun tokens(count: Long?): Int = (count ?: 0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private const val BOOK_ID = "bookId"
        private const val PLAN_ID = "planId"
        private const val ITEM_KEY = "itemKey"
        private const val SECTION_GONE = "post.section_gone"
        private const val PLAN_GONE = "post.plan_gone"
        private const val NOTHING_TO_WRITE = "post.nothing_to_write"
        private const val EMPTY_REPLY = "post.empty_reply"

        /**
         * One live job for a section under a plan, however often its reader asks. The plan is in the
         * key, so a job that failed under an earlier plan says nothing of the section under this one.
         */
        fun jobFor(
            accountId: UUID,
            plan: PlanView,
            itemKey: String,
        ) = NewJob(
            accountId = accountId,
            kind = JobKind.POST,
            dedupeKey = "post:${plan.id}:$itemKey",
            payload =
                mapOf(BOOK_ID to plan.bookId.toString(), PLAN_ID to plan.id.toString(), ITEM_KEY to itemKey),
        )
    }
}
