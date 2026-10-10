package gloss.book.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

@Entity
@DynamicUpdate
class Book private constructor(
    id: UUID,
    accountId: UUID,
    draft: BookDraft,
    source: BookSource,
    now: Instant,
) : BaseEntity(id) {
    var accountId: UUID = accountId
        protected set
    var title: String = draft.title.value
        protected set
    var authors: Array<String> = draft.authors.toTypedArray()
        protected set
    var publisher: String? = draft.publisher
        protected set
    var isbn13: String? = draft.isbn13?.value
        protected set
    var edition: String? = null
        protected set

    @Convert(converter = BookSource.Column::class)
    var source: BookSource = source
        protected set
    var sourceRef: String? = null
        protected set
    var currentTocId: UUID? = null
        protected set
    var createdAt: Instant = now
        protected set
    var deletedAt: Instant? = null
        protected set

    /** The version the reader confirmed is the one plans and posts are made from. */
    fun use(toc: Toc) {
        check(toc.bookId == id && toc.status == TocStatus.CONFIRMED) { "Only this book's confirmed contents" }
        currentTocId = toc.id
    }

    companion object {
        /** Typed in by the reader, title first, contents after. */
        fun createManual(
            accountId: UUID,
            draft: BookDraft,
            now: Instant,
        ) = Book(id = UuidV7.at(now), accountId = accountId, draft = draft, source = BookSource.MANUAL, now = now)
    }
}

/** What a reader gave for a book, each part already checked. */
data class BookDraft(
    val title: BookTitle,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: Isbn13?,
)

enum class BookSource(
    val code: String,
) {
    MANUAL("manual"),
    KAKAO("kakao"),
    NLK("nlk"),
    OPEN_LIBRARY("openlibrary"),
    ;

    @Converter
    class Column : AttributeConverter<BookSource, String> {
        override fun convertToDatabaseColumn(source: BookSource): String = source.code

        override fun convertToEntityAttribute(code: String): BookSource = entries.first { it.code == code }
    }
}
