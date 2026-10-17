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

/** The reader asked for the held edition's contents from another source than the bookshop's. */
data class ReaderSought(
    val ref: String,
) : SetupContent

/**
 * The reader started with the held contents; [tocId] is the confirmed version of the book made from
 * them, and [level] and [voice] the codes of how its posts are written, none before the choice.
 */
data class ReaderConfirmed(
    val tocId: UUID,
    val level: String? = null,
    val voice: String? = null,
) : SetupContent

/**
 * What a search for [query] found, best match first, none being an answer too. Only each edition's
 * ref is kept: the source's terms forbid a database of its books, so a list is shown by asking again.
 * [callId] names the model's call that asked, when a model did.
 */
data class BooksFound(
    val query: String,
    val books: List<FoundEdition>,
    val callId: String? = null,
) : SetupContent

/**
 * The editions the reader was shown, so the only ones they may pick: a search's list from before models
 * took part, a list the model showed, and the books it recommended. A search the model ran on its way
 * to an answer stays hidden, and none of it is offered.
 */
object ShownEditions {
    fun of(contents: List<SetupContent>): List<FoundEdition> {
        val searches = contents.filterIsInstance<BooksFound>()
        val shown = mutableListOf<FoundEdition>()
        contents.forEachIndexed { i, content ->
            when (content) {
                is BooksFound -> {
                    if (content.callId == null) shown += content.books
                }

                // The search it names is the latest one before it with that call.
                is BooksShown -> {
                    contents
                        .subList(0, i)
                        .filterIsInstance<BooksFound>()
                        .lastOrNull { it.callId == content.booksCallId }
                        ?.let { shown += it.books }
                }

                is Recommended -> {
                    shown +=
                        content.books.mapNotNull { book ->
                            searches
                                .lastOrNull { found -> found.books.any { it.ref == book.ref } }
                                ?.books
                                ?.firstOrNull { it.ref == book.ref }
                        }
                }

                else -> {}
            }
        }
        return shown
    }
}

/** An edition a search showed; [hasContents] decides whether it is picked or its contents typed in. */
data class FoundEdition(
    val ref: String,
    val hasContents: Boolean,
)

/** A search or a model turn that gave no answer to use, with the error the reader was shown. */
data class SearchFailed(
    val code: String,
    val message: String,
) : SetupContent

/**
 * The contents held for the reader to look over before any book is made: an edition's, named by
 * [ref], as the source gave them or as the reader [typed] them, or a book of their own. [title] is
 * kept as the book made from them would keep it. [thin] contents are too coarse to write from, so a
 * search for detailed ones starts with them; contents found on the web name the page they came from
 * by its [sourceUrl] and [sourceTitle].
 */
data class ContentsBrought(
    val ref: String?,
    val title: String,
    val items: Int,
    val typed: Boolean,
    val thin: Boolean = false,
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
) : SetupContent

/** The book is made and its contents confirmed, [items] of them, none counted before the plan was shown. */
data class SetupDone(
    val bookId: UUID,
    val items: Int? = null,
) : SetupContent

/** A model call that ended asking for tools: what it said with them, and the [calls] as it wrote them. */
data class ToolsCalled(
    val text: String,
    val calls: List<CalledTool>,
) : SetupContent

/** [arguments] is the raw text the model wrote, kept even when it is not valid JSON. */
data class CalledTool(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * A web search the model asked for and how many pages came back. The pages themselves are not kept:
 * the search's terms grant no right to store them, so they are read only within the turn that asked.
 */
data class WebSearched(
    val callId: String,
    val query: String,
    val results: Int,
) : SetupContent

/**
 * A page the model read while looking for contents: its address, title and length. Its text is not
 * kept: the page's terms grant no right to store it, so it is read only within the turn that asked.
 */
data class PageFetched(
    val callId: String,
    val url: String,
    val title: String,
    val characters: Int,
) : SetupContent

/** A tool call that was not run, or ran and failed, and why, as the model was told. */
data class ToolRefused(
    val callId: String,
    val problem: String,
) : SetupContent

/**
 * The editions the model recommended, best first, each one a search in this session found. A cited
 * page keeps only its address and title, so the reader can open it.
 */
data class Recommended(
    val callId: String,
    val intro: String,
    val books: List<RecommendedBook>,
) : SetupContent

data class RecommendedBook(
    val ref: String,
    val reason: String,
    val sources: List<CitedPage>,
)

data class CitedPage(
    val url: String,
    val title: String,
)

/** The model showed the whole list its book search [booksCallId] found, to pick an edition from. */
data class BooksShown(
    val callId: String,
    val booksCallId: String,
) : SetupContent

/** What the model said, when it answered in words alone. */
data class GlossSaid(
    val text: String,
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
    SEEK("seek", SetupRole.READER, ReaderSought::class.java),
    BOOKS("books", SetupRole.GLOSS, BooksFound::class.java),
    SEARCH_FAILED("search_failed", SetupRole.GLOSS, SearchFailed::class.java),
    CONTENTS("contents", SetupRole.GLOSS, ContentsBrought::class.java),
    DONE("done", SetupRole.GLOSS, SetupDone::class.java),
    TOOL_CALLS("tool_calls", SetupRole.GLOSS, ToolsCalled::class.java),
    WEB_SEARCHED("web_searched", SetupRole.GLOSS, WebSearched::class.java),
    PAGE_FETCHED("page_fetched", SetupRole.GLOSS, PageFetched::class.java),
    TOOL_REFUSED("tool_refused", SetupRole.GLOSS, ToolRefused::class.java),
    RECOMMEND("recommend", SetupRole.GLOSS, Recommended::class.java),
    BOOKS_SHOWN("books_shown", SetupRole.GLOSS, BooksShown::class.java),
    SAID("said", SetupRole.GLOSS, GlossSaid::class.java),
    ;

    companion object {
        fun of(content: SetupContent): SetupKind = entries.first { it.type.isInstance(content) }

        fun of(code: String): SetupKind = entries.first { it.code == code }
    }
}
