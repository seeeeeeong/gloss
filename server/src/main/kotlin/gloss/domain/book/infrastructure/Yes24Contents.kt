package gloss.domain.book.infrastructure

/**
 * YES24 gives a book's contents as its page shows them, each publisher's own way: `<br>` and
 * `<STRONG>`, a mark before each chapter, underscores for an indent. This takes the markup off and
 * leaves one item a line, numbering and indent as they were, for the reader to check.
 */
internal object Yes24Contents {
    // With the line end after it, so a page that writes both does not get a blank line between every two.
    // An e-book's contents break lines with "</br>" alone.
    private val BREAK = Regex("""(?i)(?:</?br\s*/?>|</(?:p|div|li|tr|h[1-6])>)[ \t]*\r?\n?""")

    // Only tags that format: a title may hold one of its own, as "<script> 태그" does.
    private val FORMAT =
        Regex("""(?i)</?(?:strong|b|em|i|u|p|div|span|font|center|li|ul|ol|table|tbody|tr|td|h[1-6])(?:\s[^<>]*)?/?>""")
    private val MARK = Regex("""^[▣■□●○◎◆◇▶▷]+\s*""")
    private val RULE = Regex("""^_+""")
    private val BLANK_RUN = Regex("""\n{3,}""")

    // Ampersand last, so "&amp;lt;" stays the "&lt;" it stood for.
    private val ENTITIES =
        listOf(
            "&nbsp;" to " ",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&amp;" to "&",
        )

    /** Null when nothing is left. */
    fun clean(raw: String): String? {
        val text =
            ENTITIES.fold(
                raw.replace(BREAK, "\n").replace(FORMAT, ""),
            ) { left, (entity, char) -> left.replace(entity, char) }
        return text
            .lines()
            .joinToString("\n", transform = ::lineOf)
            .replace(BLANK_RUN, "\n\n")
            .trim('\n')
            .takeIf { it.isNotBlank() }
    }

    private fun lineOf(line: String): String {
        val indent = line.takeWhile { it.isWhitespace() }
        val marked = line.substring(indent.length).trimEnd().replace(MARK, "")
        val rule = RULE.find(marked)?.value.orEmpty()
        val content = marked.substring(rule.length)
        // One space before a line is how the page wrapped it, not a level; a run of underscores is.
        val kept = if (indent == " ") "" else indent
        return if (content.isEmpty()) "" else kept + " ".repeat(rule.length) + content
    }
}
