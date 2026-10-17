package gloss.domain.book.domain.style

/**
 * How a book's posts are written, as the reader chose when adding it. The codes are stored with the
 * book: a new way of writing is a new code, never a changed one.
 */
data class WritingStyle(
    val level: Level,
    val voice: Voice,
) {
    /** Who the posts are written for. */
    enum class Level(
        val code: String,
    ) {
        INTRO("intro"),
        COURSE("course"),
        WORK("work"),
        ;

        companion object {
            fun of(code: String): Level? = entries.firstOrNull { it.code == code }
        }
    }

    /** How a post's sentences read. */
    enum class Voice(
        val code: String,
    ) {
        TEXTBOOK("textbook"),
        EXPLAIN("explain"),
        NOTE("note"),
        ;

        companion object {
            fun of(code: String): Voice? = entries.firstOrNull { it.code == code }
        }
    }

    companion object {
        /** A book added without choosing, and every book from before the choice: posts read as they always did. */
        val DEFAULT = WritingStyle(Level.COURSE, Voice.TEXTBOOK)
    }
}
