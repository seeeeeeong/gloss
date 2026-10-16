package gloss.domain.setup.service

import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.SetupTurnPolicy
import gloss.domain.setup.domain.WebPage
import java.util.UUID

/**
 * One model turn as it runs: the editions this session's book searches have returned, and this turn's
 * web pages and tool counts. Kept in memory only; what lasts is in the session's messages.
 */
class SetupTurn(
    val accountId: UUID,
    val sessionId: UUID,
    offered: Collection<String>,
) {
    private val offered = offered.toMutableSet()
    private val pages = linkedMapOf<String, String>()
    private val searches = mutableListOf<BooksFound>()
    private val used = mutableMapOf<SetupTurnPolicy.Tool, Int>()

    var answered = false
        private set

    fun used(tool: SetupTurnPolicy.Tool): Int = used[tool] ?: 0

    fun count(tool: SetupTurnPolicy.Tool) {
        used[tool] = used(tool) + 1
    }

    fun found(books: BooksFound) {
        searches += books
        offered += books.books.map { it.ref }
    }

    fun read(page: WebPage) {
        pages.putIfAbsent(page.url, page.title)
    }

    fun offered(): Set<String> = offered.toSet()

    fun pages(): Map<String, String> = pages.toMap()

    /** This turn's latest search for [query], told apart regardless of case and spacing at its ends. */
    fun searchedFor(query: String): BooksFound? =
        searches.lastOrNull { it.query.trim().equals(query, ignoreCase = true) }

    fun answer() {
        answered = true
    }
}
