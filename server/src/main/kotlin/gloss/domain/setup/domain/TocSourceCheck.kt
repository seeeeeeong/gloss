package gloss.domain.setup.domain

import java.text.Normalizer

/**
 * Whether contents the model submitted were copied from the page it cites, never composed: every
 * title is on the page in the order submitted, the bookshop's numbered top-level titles are among
 * them in their order, and there is more to them than the bookshop's own. Titles are compared with
 * case, spacing and punctuation set aside, as a page lays them out its own way.
 */
object TocSourceCheck {
    // Enough for the model to see what to fix, short enough to keep its prompt small.
    private const val MAX_NAMED = 5
    private const val MAX_TITLE_SHOWN = 80

    // A title of one letter is in nearly any other, so it stands for no part.
    private const val MIN_ANCHOR = 2

    /** An item as the contents were parsed into a tree. */
    data class Item(
        val depth: Int,
        val number: String?,
        val title: String,
    )

    /** The bookshop's contents: its numbered top-level titles in order, and how many items it has in all. */
    data class Bookshop(
        val anchors: List<String>,
        val items: Int,
    )

    sealed interface Result {
        data object Accepted : Result

        /** [problem] is what the model is told. */
        data class Refused(
            val problem: String,
        ) : Result
    }

    fun check(
        items: List<Item>,
        page: String,
        bookshop: Bookshop,
    ): Result {
        val problem =
            detailProblem(items, bookshop) ?: pageProblem(items, page) ?: anchorProblem(items, bookshop.anchors)
        return problem?.let(Result::Refused) ?: Result.Accepted
    }

    private fun detailProblem(
        items: List<Item>,
        bookshop: Bookshop,
    ): String? =
        if (items.size <= bookshop.items) {
            "these ${items.size} items are no more detailed than the bookshop's ${bookshop.items}; " +
                "find a page that lists the chapters and sections"
        } else {
            null
        }

    // Each title from where the one before it was found, so the contents follow the page's order.
    private fun pageProblem(
        items: List<Item>,
        page: String,
    ): String? {
        val text = keyOf(page)
        var from = 0
        val missing = mutableListOf<String>()
        for (item in items) {
            val key = keyOf(item.title)
            val at = if (key.isEmpty()) -1 else text.indexOf(key, from)
            if (at < 0) missing += item.title else from = at + key.length
        }
        if (missing.isEmpty()) return null
        return "${missing.size} titles are not on the page in this order, as " + named(missing) +
            "; copy every title exactly as the page writes it, in its order"
    }

    private fun anchorProblem(
        items: List<Item>,
        anchors: List<String>,
    ): String? {
        val keys = items.map { keyOf(it.title) }
        var next = 0
        for (anchor in anchors) {
            val key = keyOf(anchor)
            if (key.length < MIN_ANCHOR) continue
            // The bookshop's title within the item's, as "Part 1 개관" holds "개관"; never the other way,
            // or a chapter "프로세스" would stand for a part "프로세스 관리".
            val at =
                (next until keys.size).firstOrNull { keys[it].contains(key) }
                    ?: return "the bookshop's part ${quoted(anchor)} is missing or out of order; " +
                        "these may be another edition's contents"
            next = at + 1
        }
        return null
    }

    private fun named(titles: List<String>): String {
        val shown = titles.take(MAX_NAMED).joinToString(", ", transform = ::quoted)
        return if (titles.size > MAX_NAMED) "$shown and ${titles.size - MAX_NAMED} more" else shown
    }

    private fun quoted(title: String): String = "\"" + title.take(MAX_TITLE_SHOWN) + "\""

    /** Letters and digits alone, folded: full-width forms, case, spacing and punctuation set aside. */
    private fun keyOf(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase()
            .filter { it.isLetterOrDigit() }
}
