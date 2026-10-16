package gloss.domain.setup.domain

import java.time.Instant
import java.util.UUID

/** One message of a setup session, numbered in order from 1. Kept as written: a correction is a new one. */
data class SetupMessage(
    val seq: Int,
    val content: SetupContent,
    val createdAt: Instant,
)

/**
 * What a message says. Stored as its [SetupKind] and its fields as JSON, so renaming a field or a
 * kind strands the messages already kept.
 */
sealed interface SetupContent

/** The reader's own words. */
data class ReaderSaid(
    val text: String,
) : SetupContent

/** The reader picked an edition a search found. */
data class ReaderPicked(
    val ref: String,
    val title: String,
) : SetupContent

/** The reader typed in contents, for an edition the source has none for or for a book of their own. */
data class ReaderTyped(
    val title: String,
) : SetupContent

/** The reader started with the held contents; [tocId] is the confirmed version of the book made from them. */
data class ReaderConfirmed(
    val tocId: UUID,
) : SetupContent

/**
 * What a search for [query] found, best match first, none being an answer too. Only each edition's
 * ref is kept: the source's terms forbid a database of its books, so a list is shown by asking again.
 */
data class BooksFound(
    val query: String,
    val books: List<FoundEdition>,
) : SetupContent

/** An edition a search showed; [hasContents] decides whether it is picked or its contents typed in. */
data class FoundEdition(
    val ref: String,
    val hasContents: Boolean,
)

/** A search that gave no answer to use, with the error the reader was shown. */
data class SearchFailed(
    val code: String,
    val message: String,
) : SetupContent

/**
 * The contents held for the reader to look over before any book is made: an edition's, named by
 * [ref], as the source gave them or as the reader [typed] them, or a book of their own. [title] is
 * kept as the book made from them would keep it.
 */
data class ContentsBrought(
    val ref: String?,
    val title: String,
    val items: Int,
    val typed: Boolean,
) : SetupContent

/** The book is made and its contents confirmed: it is ready to read. */
data class SetupDone(
    val bookId: UUID,
) : SetupContent

enum class SetupRole(
    val code: String,
) {
    READER("reader"),
    GLOSS("gloss"),
}

enum class SetupKind(
    val code: String,
    val role: SetupRole,
    val type: Class<out SetupContent>,
) {
    TEXT("text", SetupRole.READER, ReaderSaid::class.java),
    PICK("pick", SetupRole.READER, ReaderPicked::class.java),
    TYPED("typed", SetupRole.READER, ReaderTyped::class.java),
    CONFIRM("confirm", SetupRole.READER, ReaderConfirmed::class.java),
    BOOKS("books", SetupRole.GLOSS, BooksFound::class.java),
    SEARCH_FAILED("search_failed", SetupRole.GLOSS, SearchFailed::class.java),
    CONTENTS("contents", SetupRole.GLOSS, ContentsBrought::class.java),
    DONE("done", SetupRole.GLOSS, SetupDone::class.java),
    ;

    companion object {
        fun of(content: SetupContent): SetupKind = entries.first { it.type.isInstance(content) }

        fun of(code: String): SetupKind = entries.first { it.code == code }
    }
}
