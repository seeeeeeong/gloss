package gloss.domain.setup.domain

/** Whether a bookshop's contents are too coarse to write a post per item from, by their shape alone. */
object ContentsDetailPolicy {
    /**
     * A flat list this short is parts or a handful of chapters, each far too broad for one post: YES24
     * gives 운영체제 10판, 1,033 pages, as its 11 parts. Fifteen flat items or more, or any item under
     * another, are chapters at least, and are written from as they are unless the reader asks.
     */
    const val MIN_FLAT_ITEMS = 15

    /** [depths] are the items' depths, 1 at the top. */
    fun thin(depths: List<Int>): Boolean = depths.isNotEmpty() && depths.all { it == 1 } && depths.size < MIN_FLAT_ITEMS
}
