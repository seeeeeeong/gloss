package gloss.domain.plan.service

import java.util.UUID

/**
 * A book's plan as a post is written from it. [canonicalJson] goes into a post's prompt byte for
 * byte; [kinds] has one for each item of the contents, in their order.
 */
data class PlanView(
    val id: UUID,
    val bookId: UUID,
    val tocId: UUID,
    val canonicalJson: String,
    val kinds: List<PlanItemKind>,
)

enum class PlanItemKind {
    /** A post that explains the item's subject. */
    LESSON,

    /** A short post on an item that has items under it. */
    OVERVIEW,

    /** Nothing to learn from, so no post is written. */
    NONE,
}
