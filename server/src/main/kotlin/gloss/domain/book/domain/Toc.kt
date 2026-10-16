package gloss.domain.book.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** One version of a book's contents, kept as given. An edit is a new version; confirming is the only change. */
@Entity
@DynamicUpdate
class Toc private constructor(
    id: UUID,
    book: Book,
    versionNo: Int,
    origin: TocOrigin,
    text: TocText,
    now: Instant,
) : BaseEntity(id) {
    var accountId: UUID = book.accountId
        protected set
    var bookId: UUID = book.id
        protected set
    var versionNo: Int = versionNo
        protected set

    @Convert(converter = TocOrigin.Column::class)
    var origin: TocOrigin = origin
        protected set
    var sourceUrl: String? = null
        protected set
    var raw: String = text.value
        protected set
    var rawSha256: String = sha256(text.value)
        protected set

    @Convert(converter = TocStatus.Column::class)
    var status: TocStatus = TocStatus.DRAFT
        protected set
    var confirmedAt: Instant? = null
        protected set
    var createdAt: Instant = now
        protected set

    /** False when already confirmed: nothing changes. */
    fun confirm(at: Instant): Boolean {
        if (status == TocStatus.CONFIRMED) return false
        status = TocStatus.CONFIRMED
        confirmedAt = at
        return true
    }

    companion object {
        fun createManual(
            book: Book,
            versionNo: Int,
            text: TocText,
            now: Instant,
        ) = Toc(
            id = UuidV7.at(now),
            book = book,
            versionNo = versionNo,
            origin = TocOrigin.MANUAL,
            text = text,
            now = now,
        )

        /** A sourced book's first version: what the source gave, with the page it is shown on. */
        fun createSourced(
            book: Book,
            text: TocText,
            now: Instant,
        ) = Toc(id = UuidV7.at(now), book = book, versionNo = 1, origin = TocOrigin.API, text = text, now = now)
            .also { it.sourceUrl = book.sourceUrl }

        private fun sha256(text: String): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}

enum class TocOrigin(
    val code: String,
) {
    API("api"),
    MANUAL("manual"),
    ;

    @Converter
    class Column : AttributeConverter<TocOrigin, String> {
        override fun convertToDatabaseColumn(origin: TocOrigin): String = origin.code

        override fun convertToEntityAttribute(code: String): TocOrigin = entries.first { it.code == code }
    }
}

enum class TocStatus(
    val code: String,
) {
    DRAFT("draft"),
    CONFIRMED("confirmed"),
    ;

    @Converter
    class Column : AttributeConverter<TocStatus, String> {
        override fun convertToDatabaseColumn(status: TocStatus): String = status.code

        override fun convertToEntityAttribute(code: String): TocStatus = entries.first { it.code == code }
    }
}
