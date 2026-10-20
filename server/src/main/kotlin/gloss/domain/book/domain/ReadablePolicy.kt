package gloss.domain.book.domain

/**
 * The items a book is read in. Front and back matter, the unnumbered top-level items before the
 * first one that holds a number (추천사, 들어가며) and after the last (용어집, 찾아보기), are left out
 * with what is under them; a book that numbers nothing keeps them all. The reader's contents leave
 * out the same, so nothing is planned or written for an item no reader can open.
 */
object ReadablePolicy {
    /** [parent] is the place of the item's parent in the same list. */
    data class Entry(
        val parent: Int?,
        val number: String?,
    )

    /** The places in [entries] a book is read in, in order. */
    fun readable(entries: List<Entry>): List<Int> {
        val all = entries.indices.toList()
        val roots = all.map { rootOf(entries, it) }
        val tops = all.filter { roots[it] == it }
        // Numbered when it or anything under it holds a number, as an unnumbered part over chapter 1.
        val numbered = all.filter { !entries[it].number.isNullOrEmpty() }.map { roots[it] }.toSet()
        val first = tops.indexOfFirst { it in numbered }
        val last = tops.indexOfLast { it in numbered }
        if (first == -1 || (first == 0 && last == tops.lastIndex)) return all
        val kept = tops.subList(first, last + 1).toSet()
        return all.filter { roots[it] in kept }
    }

    // A parent out of the list, or a loop, ends the climb where it is.
    private fun rootOf(
        entries: List<Entry>,
        place: Int,
    ): Int =
        generateSequence(place) { entries[it].parent?.takeIf(entries.indices::contains) }
            .take(entries.size + 1)
            .last()
}
