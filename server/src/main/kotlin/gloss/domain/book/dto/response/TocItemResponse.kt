package gloss.domain.book.dto.response

import gloss.domain.book.domain.TocItem
import java.util.UUID

data class TocItemResponse(
    val id: UUID,
    val parentId: UUID?,
    val depth: Int,
    val number: String?,
    val title: String,
    val itemKey: String,
) {
    companion object {
        fun from(item: TocItem) =
            TocItemResponse(
                id = item.id,
                parentId = item.parentId,
                depth = item.depth,
                number = item.number,
                title = item.title,
                itemKey = item.itemKey,
            )
    }
}
