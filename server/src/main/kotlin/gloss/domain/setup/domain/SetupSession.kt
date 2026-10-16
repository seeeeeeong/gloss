package gloss.domain.setup.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

/** A reader adding one book by talking it through. Its messages are kept apart, append-only. */
@Entity
@DynamicUpdate
class SetupSession private constructor(
    id: UUID,
    accountId: UUID,
    now: Instant,
) : BaseEntity(id) {
    var accountId: UUID = accountId
        protected set

    @Convert(converter = SetupStateColumn::class)
    var state: SetupPolicy.State = SetupPolicy.State.SEARCHING
        protected set

    /** The edition picked, while one is. */
    var editionRef: String? = null
        protected set

    /** The held book's title, kept with its contents so the two are always read together. */
    var heldTitle: String? = null
        protected set

    /** A book the reader typed in instead, while one is held: its authors, none being none typed. */
    var typedAuthors: Array<String>? = null
        protected set

    /**
     * Counts every change a reader could act on, so each request names the one it saw. A start ends
     * the session and changes nothing after it, so it leaves the count as the reader saw it.
     */
    var revision: Int = 0
        protected set

    /** When the search for the reader's latest message was asked, until it is answered. */
    var searchingSince: Instant? = null
        protected set

    /** The held contents, as the reader last left them. */
    var contents: String? = null
        protected set

    /** The book made when the reader started, and the version of its contents confirmed. */
    var bookId: UUID? = null
        protected set
    var tocId: UUID? = null
        protected set
    var createdAt: Instant = now
        protected set
    var updatedAt: Instant = now
        protected set
    var confirmedAt: Instant? = null
        protected set

    fun allows(step: SetupPolicy.Step): Boolean = SetupPolicy.next(state, step) != null

    fun searching(now: Instant): Boolean = SetupPolicy.searching(searchingSince, now)

    /** A message searches again, so a pick held until now is dropped; its answer is awaited. */
    fun say(at: Instant) {
        check(!searching(at)) { "A message waits for the search before it" }
        searchingSince = at
        state = advance(SetupPolicy.Step.SAY)
        if (state == SetupPolicy.State.SEARCHING) hold(null)
        updatedAt = at
    }

    fun answered() {
        searchingSince = null
        revision++
    }

    fun choose(
        editionRef: String,
        title: String,
        contents: String,
        at: Instant,
    ) {
        state = advance(SetupPolicy.Step.PICK)
        hold(SetupHold(editionRef = editionRef, title = title, typedAuthors = null, contents = contents))
        updatedAt = at
    }

    /** A book typed in whole, held as a picked edition is; [authors] may be none. */
    fun type(
        title: String,
        authors: List<String>,
        contents: String,
        at: Instant,
    ) {
        state = advance(SetupPolicy.Step.PICK)
        hold(SetupHold(editionRef = null, title = title, typedAuthors = authors, contents = contents))
        updatedAt = at
    }

    private fun hold(held: SetupHold?) {
        editionRef = held?.editionRef
        heldTitle = held?.title
        typedAuthors = held?.typedAuthors?.toTypedArray()
        contents = held?.contents
        revision++
    }

    fun edit(
        contents: String,
        at: Instant,
    ) {
        state = advance(SetupPolicy.Step.EDIT)
        this.contents = contents
        revision++
        updatedAt = at
    }

    fun confirm(
        bookId: UUID,
        tocId: UUID,
        at: Instant,
    ) {
        state = advance(SetupPolicy.Step.CONFIRM)
        this.bookId = bookId
        this.tocId = tocId
        updatedAt = at
        confirmedAt = at
    }

    private fun advance(step: SetupPolicy.Step): SetupPolicy.State =
        checkNotNull(SetupPolicy.next(state, step)) { "$step is not open while $state" }

    companion object {
        fun start(
            accountId: UUID,
            now: Instant,
        ) = SetupSession(id = UuidV7.at(now), accountId = accountId, now = now)
    }
}

/** What a session holds for the reader to start with: an edition, or a book they typed in with [typedAuthors]. */
data class SetupHold(
    val editionRef: String?,
    val title: String,
    val typedAuthors: List<String>?,
    val contents: String,
)

@Converter
class SetupStateColumn : AttributeConverter<SetupPolicy.State, String> {
    override fun convertToDatabaseColumn(state: SetupPolicy.State): String = state.code

    override fun convertToEntityAttribute(code: String): SetupPolicy.State =
        SetupPolicy.State.entries.first { it.code == code }
}
