package gloss.domain.plan.service

import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.book.service.ContentsInUse
import gloss.domain.book.service.TocService
import gloss.domain.generation.domain.job.JobStatus
import gloss.domain.generation.service.GenerationService
import gloss.domain.plan.domain.BookPlan
import gloss.domain.plan.domain.BookPlanDraft
import gloss.domain.plan.domain.event.PlanKept
import gloss.domain.plan.domain.repository.BookPlanRepository
import gloss.domain.plan.dto.response.PlanStatusResponse
import gloss.domain.plan.infrastructure.PlanJson
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** A book's plans: asked for once its contents are confirmed, and kept as written. */
@Service
class PlanService(
    private val bookPlanRepository: BookPlanRepository,
    private val tocService: TocService,
    private val generationService: GenerationService,
    private val planJson: PlanJson,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    /**
     * Asks for the plan of confirmed contents. Null when they have one; a request already waiting or
     * running is the one given back, so asking twice writes, and bills, one plan.
     */
    @Transactional
    fun request(
        accountId: UUID,
        bookId: UUID,
        tocId: UUID,
    ): UUID? {
        if (bookPlanRepository.existsByTocIdAndAccountId(tocId, accountId)) return null
        return generationService.enqueue(PlanJobHandler.jobFor(accountId = accountId, bookId = bookId, tocId = tocId))
    }

    /**
     * As [request], but only for contents never asked for before. An event may be delivered twice,
     * and a plan that failed is written again when its reader asks, never because an event came back.
     */
    @Transactional
    fun requestOnce(
        accountId: UUID,
        bookId: UUID,
        tocId: UUID,
    ): UUID? {
        if (progress(accountId, tocId) != null) return null
        return request(accountId = accountId, bookId = bookId, tocId = tocId)
    }

    @Transactional(readOnly = true)
    fun exists(
        accountId: UUID,
        tocId: UUID,
    ): Boolean = bookPlanRepository.existsByTocIdAndAccountId(tocId, accountId)

    /** The plan posts of these contents are written from; null until one is kept. */
    @Transactional(readOnly = true)
    fun find(
        accountId: UUID,
        tocId: UUID,
    ): PlanView? {
        val plan = bookPlanRepository.findTopByTocIdAndAccountIdOrderByVersionNoDesc(tocId, accountId) ?: return null
        return PlanView(
            id = plan.id,
            bookId = plan.bookId,
            tocId = plan.tocId,
            canonicalJson = plan.canonicalJson,
            kinds = planJson.kinds(plan.canonicalJson).mapValues { PlanItemKind.valueOf(it.value.name) },
        )
    }

    /**
     * How far the plan of the book's contents in use has come, for a reader waiting on it. Confirming
     * the contents asks for it, so for a while after that no job is still planning; past [NOT_STARTED]
     * the ask was lost, as when its listener failed, and it is failed, for the reader to ask again. A
     * job that ended without a plan, failed or cancelled, is failed too.
     */
    @Transactional(readOnly = true)
    fun status(
        accountId: UUID,
        bookId: UUID,
    ): PlanStatusResponse {
        val contents = inUse(accountId, bookId)
        if (bookPlanRepository.existsByTocIdAndAccountId(contents.tocId, accountId)) return PlanStatusResponse.READY
        val job = progress(accountId, contents.tocId)
        if (job == null) {
            val lost = contents.confirmedAt.plus(NOT_STARTED).isBefore(clock.instant())
            return if (lost) PlanStatusResponse.failed(NOT_STARTED_CODE) else PlanStatusResponse.PLANNING
        }
        if (job.state.live) return PlanStatusResponse.PLANNING
        return PlanStatusResponse.failed(job.errorKind ?: "plan.${job.state.code}")
    }

    /**
     * The reader asks again for a plan that ended without one, or was never asked for; a plan kept,
     * or one still to come, is left as it is, so asking twice writes, and bills, one plan.
     */
    @Transactional
    fun retry(
        accountId: UUID,
        bookId: UUID,
    ): PlanStatusResponse {
        val tocId = inUse(accountId, bookId).tocId
        if (bookPlanRepository.existsByTocIdAndAccountId(tocId, accountId)) return PlanStatusResponse.READY
        if (progress(accountId, tocId)?.state?.live != true) {
            request(accountId = accountId, bookId = bookId, tocId = tocId)
        }
        return PlanStatusResponse.PLANNING
    }

    private fun inUse(
        accountId: UUID,
        bookId: UUID,
    ): ContentsInUse = tocService.inUse(accountId, bookId) ?: throw GlossException(PlanError.BOOK_NOT_FOUND)

    /** How the job writing these contents' plan stands; null when none was ever asked for. */
    fun progress(
        accountId: UUID,
        tocId: UUID,
    ): JobStatus? = generationService.latest(accountId, PlanJobHandler.keyOf(tocId))

    /**
     * Null, and nothing added, when the contents already have a plan, or are no longer the ones in
     * use: confirmed anew while the plan was written, no post would be written from it.
     */
    @Transactional
    fun keep(draft: BookPlanDraft): UUID? {
        val inUse = tocService.holdCurrentTocId(draft.accountId, draft.bookId) == draft.tocId
        if (!inUse || bookPlanRepository.existsByTocIdAndAccountId(draft.tocId, draft.accountId)) return null
        val now = clock.instant()
        val plan = BookPlan.create(draft = draft, versionNo = 1, now = now)
        bookPlanRepository.save(plan)
        applicationEventPublisher.publishEvent(
            PlanKept(
                eventId = UuidV7.at(now),
                occurredAt = now,
                accountId = plan.accountId,
                bookId = plan.bookId,
                planId = plan.id,
            ),
        )
        return plan.id
    }

    private companion object {
        // The listener asks within moments of the commit; a minute with no job means its ask was lost.
        val NOT_STARTED: Duration = Duration.ofMinutes(1)
        const val NOT_STARTED_CODE = "plan.not_started"
    }
}
