package gloss.domain.post.service

import gloss.domain.book.service.OutlineItem
import gloss.domain.book.service.TocService
import gloss.domain.budget.service.Purpose
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.service.JobCallService
import gloss.domain.generation.service.JobFailed
import gloss.domain.generation.service.JobHandler
import gloss.domain.generation.service.RunningJob
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.plan.service.PlanItemKind
import gloss.domain.plan.service.PlanService
import gloss.domain.plan.service.PlanView
import gloss.domain.post.domain.PostCheck
import gloss.domain.post.domain.PostContent
import gloss.domain.post.domain.PostPrompt
import gloss.domain.post.domain.PostVersion
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Writes the post of one section in one model call, from the plan of the contents in use, and once
 * more when [PostCheck] fails it. Only a writing that passed is kept, and only while those contents
 * are still the ones in use.
 */
@Component
class PostJobHandler(
    private val tocService: TocService,
    private val planService: PlanService,
    private val postService: PostService,
    private val jobCallService: JobCallService,
    private val postCheckService: PostCheckService,
) : JobHandler {
    override val kind = JobKind.POST

    override fun handle(job: RunningJob): () -> Unit {
        val bookId = UUID.fromString(job.payload.getValue(BOOK_ID))
        val itemKey = job.payload.getValue(ITEM_KEY)
        if (postService.freshness(job.accountId, bookId, itemKey) == PostFreshness.FRESH) return {}
        val section = sectionOf(job.accountId, Asked(bookId, UUID.fromString(job.payload.getValue(PLAN_ID)), itemKey))
        // Contents confirmed anew before a rewrite: nothing written now could be kept, as below.
        val reply = passingReply(job, section) ?: return {}
        if (PostContent.of(reply.text) == null) throw JobFailed(UNSTORABLE, retryable = false)
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
        val section = Section(bookId = asked.bookId, item = item, plan = plan)
        requireSomethingToWrite(section, readable = item in outline.readableItems)
        return section
    }

    // Front and back matter is never read, and an item the plan leaves out has nothing to write from.
    private fun requireSomethingToWrite(
        section: Section,
        readable: Boolean,
    ) {
        val kind = section.plan.kinds[section.item.ord]
        if (!readable || kind == null || kind == PlanItemKind.NONE) throw JobFailed(NOTHING_TO_WRITE, retryable = false)
    }

    /**
     * A writing that passed its check. One that failed is written once more, the same call again, and
     * the count holds across the job's attempts: a writing whose call failed on its way is spent too.
     * Null when the contents changed before a rewrite.
     */
    private fun passingReply(
        job: RunningJob,
        section: Section,
    ): LlmReply? {
        val call = callFor(job.accountId, section)
        for (writing in postCheckService.taken(job) + 1..WRITINGS) {
            if (writing > 1 && !inUse(job.accountId, section)) return null
            // Taken by an attempt that holds the job, or no call: two attempts racing write twice at most.
            if (!postCheckService.take(job, writing)) throw JobFailed(WRITING_TAKEN, retryable = false)
            val reply = jobCallService.answer(job, call, PostPrompt.VERSION)
            val verdict = postCheckService.judge(postWritingOf(job, section, Answered(writing, reply)))
            if (verdict.passed) return reply
        }
        throw JobFailed(CHECK_FAILED, retryable = false)
    }

    private fun inUse(
        accountId: UUID,
        section: Section,
    ): Boolean = tocService.outline(accountId, section.bookId)?.tocId == section.plan.tocId

    private class Answered(
        val writing: Int,
        val reply: LlmReply,
    )

    private fun postWritingOf(
        job: RunningJob,
        section: Section,
        answered: Answered,
    ) = PostWriting(
        jobId = job.jobId,
        attempt = job.attempt,
        writing = answered.writing,
        accountId = job.accountId,
        bookId = section.bookId,
        planId = section.plan.id,
        itemKey = section.item.itemKey,
        promptVersion = PostPrompt.VERSION,
        form = formOf(section),
        finishReason = answered.reply.finishReason.code,
        text = answered.reply.text,
    )

    // An overview is the plan's only other kind with a post; a section with none never gets here.
    private fun formOf(section: Section): PostCheck.Form =
        if (section.plan.kinds[section.item.ord] == PlanItemKind.OVERVIEW) {
            PostCheck.Form.OVERVIEW
        } else {
            PostCheck.Form.LESSON
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
        private const val CHECK_FAILED = "post.check_failed"
        private const val UNSTORABLE = "post.unstorable_reply"
        private const val WRITING_TAKEN = "post.writing_taken"

        // One rewrite catches a passing slip; a second failure is more likely the prompt or the plan,
        // which another paid call would rarely mend. A bad post costs two calls at most.
        private const val WRITINGS = 2

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
