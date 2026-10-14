package gloss.plan.application

import gloss.book.application.TocService
import gloss.common.util.UuidV7
import gloss.generation.application.GenerationService
import gloss.generation.domain.job.JobStatus
import gloss.plan.domain.BookPlan
import gloss.plan.domain.BookPlanDraft
import gloss.plan.domain.BookPlanRepository
import gloss.plan.domain.event.PlanKept
import gloss.plan.infrastructure.PlanJson
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
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
            kinds = planJson.kinds(plan.canonicalJson).map { PlanItemKind.valueOf(it.name) },
        )
    }

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
}
