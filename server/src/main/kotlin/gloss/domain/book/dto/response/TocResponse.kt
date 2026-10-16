package gloss.domain.book.dto.response

import gloss.domain.book.domain.Toc
import gloss.domain.book.domain.TocItem
import java.time.Instant
import java.util.UUID

data class TocResponse(
    val id: UUID,
    val versionNo: Int,
    val origin: String,
    val status: String,
    val confirmedAt: Instant?,
    val raw: String,
    val items: List<TocItemResponse>,
) {
    companion object {
        fun from(
            toc: Toc,
            items: List<TocItem>,
        ) = TocResponse(
            id = toc.id,
            versionNo = toc.versionNo,
            origin = toc.origin.code,
            status = toc.status.code,
            confirmedAt = toc.confirmedAt,
            raw = toc.raw,
            items = items.map(TocItemResponse::from),
        )
    }
}
