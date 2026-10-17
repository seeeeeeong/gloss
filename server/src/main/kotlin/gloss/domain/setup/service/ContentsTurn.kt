package gloss.domain.setup.service

import gloss.domain.book.service.OutlineItem
import gloss.domain.setup.domain.ContentsTurnPolicy
import gloss.domain.setup.domain.WebPage
import java.time.Instant

/**
 * One look for an edition's detailed contents as it runs: the bookshop's own contents to check against,
 * the pages search_web offered and fetch_page read, this turn's tool counts, and the contents found.
 * Kept in memory only; what lasts is in the session's messages and the page found on.
 */
class ContentsTurn(
    val asked: TurnAsked,
    val edition: Held,
    /** The bookshop's contents, none when it has none. */
    val known: List<OutlineItem>,
) {
    /** The edition held when the look started, as it was then: found contents replace only it. */
    data class Held(
        val ref: String,
        val title: String,
        val revision: Int,
    )

    /** A page as fetch_page read it: [url] as the model asked for it, [finalUrl] where it was found. */
    data class ReadPage(
        val url: String,
        val finalUrl: String,
        val title: String,
        val text: String,
        val readAt: Instant,
    )

    /** Contents that passed the check, as submitted, and the page they were checked against. */
    data class Found(
        val contents: String,
        val items: Int,
        val page: ReadPage,
    )

    private val offered = linkedSetOf<String>()
    private val pages = linkedMapOf<String, ReadPage>()
    private val used = mutableMapOf<ContentsTurnPolicy.Tool, Int>()

    var found: Found? = null
        private set

    /** Whether a page was read, so a last call could submit it. */
    val read: Boolean get() = pages.isNotEmpty()

    /** The bookshop's numbered top-level titles: what any detailed contents of the same edition hold. */
    val anchors: List<String> get() = known.filter { it.depth == 1 && it.number != null }.map { it.title }

    fun used(tool: ContentsTurnPolicy.Tool): Int = used[tool] ?: 0

    fun count(tool: ContentsTurnPolicy.Tool) {
        used[tool] = used(tool) + 1
    }

    fun offer(page: WebPage) {
        offered += page.url
    }

    fun offered(url: String): Boolean = url in offered

    fun read(page: ReadPage) {
        pages[page.url] = page
    }

    /** A page read in this turn, by the address the model asked for or the one it was found at. */
    fun page(url: String): ReadPage? = pages[url] ?: pages.values.firstOrNull { it.finalUrl == url }

    fun find(contents: Found) {
        found = contents
    }
}
