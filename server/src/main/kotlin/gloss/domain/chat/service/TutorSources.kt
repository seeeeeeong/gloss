package gloss.domain.chat.service

import gloss.common.error.GlossException
import gloss.domain.book.service.BookOutline
import gloss.domain.book.service.OutlineItem
import gloss.domain.book.service.TocService
import gloss.domain.chat.domain.TutorPrompt
import gloss.domain.plan.service.PlanService
import gloss.domain.post.service.PostService
import gloss.domain.post.service.PostText
import org.springframework.stereotype.Component
import java.util.UUID

/** What a turn is written from, read from the modules that keep it: the book's plan, the item, its post. */
@Component
class TutorSources(
    private val tocService: TocService,
    private val planService: PlanService,
    private val postService: PostService,
) {
    /** Refused when the book has no confirmed contents or plan yet, or [itemKey] is not in them. */
    fun of(
        accountId: UUID,
        bookId: UUID,
        itemKey: String?,
    ): Sources {
        val outline = tocService.outline(accountId, bookId) ?: throw GlossException(ChatError.BOOK_NOT_READY)
        val item = itemKey?.let { itemOf(outline, it) }
        val plan = planService.find(accountId, outline.tocId) ?: throw GlossException(ChatError.NO_PLAN)
        return Sources(planId = plan.id, planJson = plan.canonicalJson, item = item)
    }

    /** The version of the item's post the reader was [shown], else the current one; null before it is written. */
    fun post(
        accountId: UUID,
        bookId: UUID,
        item: OutlineItem,
        shown: UUID?,
    ): PostText? = postService.current(accountId = accountId, bookId = bookId, itemKey = item.itemKey, shown = shown)

    private fun itemOf(
        outline: BookOutline,
        itemKey: String,
    ): OutlineItem =
        outline.items.firstOrNull { it.itemKey == itemKey } ?: throw GlossException(ChatError.SECTION_NOT_FOUND)
}

/** The plan in use, as posts are written from it byte for byte, and the item a turn was asked from. */
data class Sources(
    val planId: UUID,
    val planJson: String,
    val item: OutlineItem?,
) {
    val target: TutorPrompt.Target? get() =
        item?.let {
            TutorPrompt.Target(ord = it.ord, number = it.number, title = it.title)
        }
}
