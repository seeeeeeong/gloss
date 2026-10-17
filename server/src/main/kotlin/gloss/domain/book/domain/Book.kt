package gloss.domain.book.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import gloss.domain.book.domain.style.WritingStyle
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
    var sourceUrl: String? = null
        protected set
    var currentTocId: UUID? = null
        protected set

    @Convert(converter = LevelColumn::class)
    var level: WritingStyle.Level = draft.style.level
        protected set

    @Convert(converter = VoiceColumn::class)
    var voice: WritingStyle.Voice = draft.style.voice
        protected set
    var createdAt: Instant = now
        protected set
    var deletedAt: Instant? = null
        protected set

    val style: WritingStyle get() = WritingStyle(level, voice)

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

        /** Found in a source: which item it is there and its page are kept with it. */
        fun createSourced(
            accountId: UUID,
            draft: BookDraft,
            sourced: SourcedBook,
            now: Instant,
        ) = Book(id = UuidV7.at(now), accountId = accountId, draft = draft, source = sourced.source, now = now).also {
            it.sourceRef = sourced.ref
            it.sourceUrl = sourced.url
        }
    }
}

/** What a reader gave for a book, each part already checked, and how its posts are to be written. */
data class BookDraft(
    val title: BookTitle,
    val authors: List<String>,
    val publisher: String?,
    val isbn13: Isbn13?,
    val style: WritingStyle,
) {
    companion object {
        // An edited volume's contributors fit; a longer list is more likely a paste gone wrong.
        const val MAX_AUTHORS = 20

        // A name with its role, as "마틴 파울러 (지은이)", or a publisher's imprint, with room to spare.
        const val MAX_NAME = 100

        /** Same as book.publisher's check, and the length kept for each author. */
        fun fits(name: String) = name.codePointCount(0, name.length) <= MAX_NAME && name.none { it.isISOControl() }
    }
}

enum class BookSource(
    val code: String,
) {
    MANUAL("manual"),
    YES24("yes24"),
    ;

    @Converter
    class Column : AttributeConverter<BookSource, String> {
        override fun convertToDatabaseColumn(source: BookSource): String = source.code

        override fun convertToEntityAttribute(code: String): BookSource = entries.first { it.code == code }
    }
}

@Converter
class LevelColumn : AttributeConverter<WritingStyle.Level, String> {
    override fun convertToDatabaseColumn(level: WritingStyle.Level): String = level.code

    override fun convertToEntityAttribute(code: String): WritingStyle.Level = checkNotNull(WritingStyle.Level.of(code))
}

@Converter
class VoiceColumn : AttributeConverter<WritingStyle.Voice, String> {
    override fun convertToDatabaseColumn(voice: WritingStyle.Voice): String = voice.code

    override fun convertToEntityAttribute(code: String): WritingStyle.Voice = checkNotNull(WritingStyle.Voice.of(code))
}
