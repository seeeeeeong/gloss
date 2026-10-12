package gloss.book.application.dto

import gloss.book.domain.Book
import gloss.book.domain.Toc
import gloss.book.domain.TocItem
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class BookCreateRequest(
    @field:NotBlank val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val isbn13: String? = null,
    @field:NotBlank val toc: String,
)

data class TocReviseRequest(
    @field:NotBlank val toc: String,
)

data class BookSummaryResponse(
    val id: UUID,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String?,
    val source: String,
    val sourceUrl: String?,
    val confirmed: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(book: Book) =
            BookSummaryResponse(
                id = book.id,
                title = book.title,
                authors = book.authors.toList(),
                publisher = book.publisher,
                isbn13 = book.isbn13,
                source = book.source.code,
                sourceUrl = book.sourceUrl,
                confirmed = book.currentTocId != null,
                createdAt = book.createdAt,
            )
    }
}

/** The book with its latest contents, which may be a draft newer than the one in use. */
data class BookResponse(
    val id: UUID,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: String?,
    val source: String,
    val sourceUrl: String?,
    val currentTocId: UUID?,
    val toc: TocResponse,
) {
    companion object {
        fun from(
            book: Book,
            toc: Toc,
            items: List<TocItem>,
        ) = BookResponse(
            id = book.id,
            title = book.title,
            authors = book.authors.toList(),
            publisher = book.publisher,
            isbn13 = book.isbn13,
            source = book.source.code,
            sourceUrl = book.sourceUrl,
            currentTocId = book.currentTocId,
            toc = TocResponse.from(toc, items),
        )
    }
}

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
