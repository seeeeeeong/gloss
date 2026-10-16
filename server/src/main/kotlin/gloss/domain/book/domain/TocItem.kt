package gloss.domain.book.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.Entity
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

/** One line of a version of the contents. Append-only, as its version is. */
@Entity
@Immutable
class TocItem private constructor(
    id: UUID,
    toc: Toc,
    parentId: UUID?,
    parsed: TocParser.Item,
) : BaseEntity(id) {
    val accountId: UUID = toc.accountId
    val tocId: UUID = toc.id
    val parentId: UUID? = parentId
    val position: Int = parsed.position
    val ord: Int = parsed.ord
    val depth: Int = parsed.depth
    val number: String? = parsed.number
    val title: String = parsed.title
    val itemKey: String = parsed.key

    companion object {
        /** In the parsed order, so each parent is made before its children. */
        fun itemsOf(
            toc: Toc,
            parsed: List<TocParser.Item>,
            now: Instant,
        ): List<TocItem> {
            val ids = parsed.map { UuidV7.at(now) }
            return parsed.map { TocItem(id = ids[it.ord], toc = toc, parentId = it.parent?.let(ids::get), parsed = it) }
        }
    }
}
