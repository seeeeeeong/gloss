package gloss.domain.plan.service

import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.service.BookOutline
import gloss.domain.book.service.TocService
import gloss.domain.budget.service.Purpose
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.service.JobCallService
import gloss.domain.generation.service.JobFailed
import gloss.domain.generation.service.JobHandler
import gloss.domain.generation.service.RunningJob
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.ReplyFormat
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.plan.domain.BookPlanDraft
import gloss.domain.plan.domain.PlanDecision
import gloss.domain.plan.domain.PlanPolicy
import gloss.domain.plan.domain.PlanPrompt
import gloss.domain.plan.infrastructure.PlanJson
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Writes the plan of a book's confirmed contents in one model call, of the items the book is read
 * in. The reply is kept only if it plans every one; one that does not fails the job rather than
 * stand as a plan with holes.
 */
@Component
class PlanJobHandler(
    private val tocService: TocService,
    private val planService: PlanService,
    private val jobCallService: JobCallService,
    private val planJson: PlanJson,
) : JobHandler {
    override val kind = JobKind.PLAN

    override fun handle(job: RunningJob): () -> Unit {
        val bookId = UUID.fromString(job.payload.getValue(BOOK_ID))
        val tocId = UUID.fromString(job.payload.getValue(TOC_ID))
        if (planService.exists(job.accountId, tocId)) return {}
        // Confirmed anew, or the book taken off the shelf, since the job was asked for: no post would use it.
        val outline =
            tocService.outline(job.accountId, bookId)?.takeIf { it.tocId == tocId }
                ?: throw JobFailed(SUPERSEDED, retryable = false)
        val subject = subjectOf(outline)
        val reply = ask(job, subject)
        val kept =
            BookPlanDraft(
                accountId = job.accountId,
                bookId = bookId,
                tocId = tocId,
                canonicalJson = planJson.write(subject, planOf(reply.text, subject)),
                model = reply.model,
                promptVersion = PlanPrompt.VERSION,
            )
        return { planService.keep(kept) }
    }

    private fun ask(
        job: RunningJob,
        subject: PlanPrompt.Subject,
    ): LlmReply {
        val maxTokens = PlanPolicy.capFor(subject.lines.size) ?: throw JobFailed(TOO_MANY_ITEMS, retryable = false)
        val reply = jobCallService.complete(job, callFor(job.accountId, subject, maxTokens), PlanPrompt.VERSION)
        // The provider's JSON output now and then answers with nothing; that cost the prompt alone.
        if (reply.text.isBlank()) throw JobFailed(EMPTY_REPLY, retryable = true)
        return reply
    }

    private fun planOf(
        reply: String,
        subject: PlanPrompt.Subject,
    ): PlanPolicy.Plan {
        val draft = planJson.read(reply) ?: throw JobFailed(NOT_JSON, retryable = false)
        return when (val decision = PlanPolicy.accept(draft, subject.lines)) {
            is PlanDecision.Accepted -> decision.plan
            is PlanDecision.Refused -> throw JobFailed(decision.reason.code, retryable = false)
        }
    }

    private fun subjectOf(outline: BookOutline) =
        PlanPrompt.Subject(
            title = outline.title,
            authors = outline.authors,
            level = levelOf(outline.style.level),
            voice = voiceOf(outline.style.voice),
            // Front and back matter is never read, so it is not planned: every post's prompt carries the plan.
            lines =
                outline.readableItems.map {
                    PlanPrompt.Line(
                        ord = it.ord,
                        parent = it.parent,
                        depth = it.depth,
                        number = it.number,
                        title = it.title,
                    )
                },
        )

    private fun levelOf(level: WritingStyle.Level) =
        when (level) {
            WritingStyle.Level.INTRO -> PlanPrompt.Level.INTRO
            WritingStyle.Level.COURSE -> PlanPrompt.Level.COURSE
            WritingStyle.Level.WORK -> PlanPrompt.Level.WORK
        }

    private fun voiceOf(voice: WritingStyle.Voice) =
        when (voice) {
            WritingStyle.Voice.TEXTBOOK -> PlanPrompt.Voice.TEXTBOOK
            WritingStyle.Voice.EXPLAIN -> PlanPrompt.Voice.EXPLAIN
            WritingStyle.Voice.NOTE -> PlanPrompt.Voice.NOTE
        }

    // Thinking is off: it bills its reasoning as output, and a plan is drawn from the contents alone.
    private fun callFor(
        accountId: UUID,
        subject: PlanPrompt.Subject,
        maxTokens: Int,
    ) = LlmCall(
        accountId = accountId,
        purpose = Purpose.PLAN,
        messages =
            listOf(
                ChatMessage(Role.SYSTEM, PlanPrompt.SYSTEM),
                ChatMessage(Role.USER, PlanPrompt.user(subject)),
            ),
        maxTokens = maxTokens,
        thinking = Thinking.DISABLED,
        format = ReplyFormat.JSON,
    )

    companion object {
        private const val BOOK_ID = "bookId"
        private const val TOC_ID = "tocId"
        private const val SUPERSEDED = "plan.superseded"
        private const val TOO_MANY_ITEMS = "plan.too_many_items"
        private const val EMPTY_REPLY = "plan.empty_reply"
        private const val NOT_JSON = "plan.not_json"

        fun keyOf(tocId: UUID) = "plan:toc:$tocId"

        /** One live job for a version of the contents, however often it is asked for. */
        fun jobFor(
            accountId: UUID,
            bookId: UUID,
            tocId: UUID,
        ) = NewJob(
            accountId = accountId,
            kind = JobKind.PLAN,
            dedupeKey = keyOf(tocId),
            payload = mapOf(BOOK_ID to bookId.toString(), TOC_ID to tocId.toString()),
        )
    }
}
