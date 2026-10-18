package gloss.domain.book.domain

/**
 * A table of contents, one item a line, into a tree. Depth comes from a line's numbering (part,
 * chapter, 1.2.3) where it has one, otherwise from its indent; in contents that indent nothing, an
 * unnumbered line belongs to the part or chapter above it. The reader sees the tree before starting,
 * so a wrong guess shows before anything is written from it.
 */
object TocParser {
    // Same as toc_item's checks; a book's contents run to a few hundred lines.
    const val MAX_ITEMS = 2_000

    // A heading with its subtitle fits with room to spare; longer is a paragraph pasted by mistake.
    const val MAX_TITLE = 500

    // A part, a chapter and four numbered levels (1.2.3.4): a deeper line is more likely a paste gone
    // wrong than a section to write about, and is refused rather than moved up a level.
    const val MAX_DEPTH = 6

    // Same as toc_item.number's check: "1.2.3.4" or "Chapter 12" run to a dozen, so longer is no label.
    private const val MAX_NUMBER = 50

    /** [parent] and [ord] are places in the parsed list; [position] is among its siblings. */
    data class Item(
        val ord: Int,
        val parent: Int?,
        val position: Int,
        val depth: Int,
        val number: String?,
        val title: String,
        val key: String,
    )

    sealed interface Result {
        data class Parsed(
            val items: List<Item>,
        ) : Result

        data object Empty : Result

        data object TooManyItems : Result

        data object LineTooLong : Result

        data object TooDeep : Result
    }

    private data class Line(
        val indent: Int,
        val rank: Int?,
        val number: String?,
        val title: String,
        val length: Int,
    )

    private const val PART_RANK = 0
    private const val CHAPTER_RANK = 1
    private const val TAB_WIDTH = 4
    private const val IDEOGRAPHIC_SPACE_WIDTH = 2

    // A label ends at a space, a separator or the end of the line, so "1장" is one and "10년" is not.
    private const val LABEL_END = """(?=[\s.:)]|$)"""

    // A Roman numeral as one character (Ⅰ to Ⅻ) numbers nothing but a part; the letters I, V and X
    // alone could start a title, so they need "부" or "Part".
    private val PART =
        Regex("""^((?:제\s*)?(?:\d+|[IVX]+)\s*부|(?i:part)\s+(?:\d+|[IVX]+)|[\u2160-\u216B]+)$LABEL_END""")
    private val CHAPTER = Regex("""^((?:제\s*)?\d+\s*장|(?i:chapter)\s+\d+|부록\s*[A-Z]|(?i:appendix)\s+[A-Z])$LABEL_END""")
    private val DOTTED = Regex("""^(\d+(?:\.\d+)*|[A-Z](?:\.\d+)+)\.?(?=\s)""")
    private val BULLET = Regex("""^[-*•·](?:\s+|$)""")
    private val SEPARATOR = Regex("""^[\s.:)\-–—]+""")

    // What follows the last chapter and belongs to no chapter. Only checked after the last numbered
    // line, since a chapter may close with a section of the same name.
    private val BACK_MATTER =
        Regex(
            """^(?:마치며|나가며|나오는\s*글|맺음말|맺으며|에필로그|후기|부록|찾아보기|색인|참고\s*문헌|감사의\s*[글말]|용어집|미주|""" +
                """(?i:epilogue|afterword|appendix|index|bibliography|references|glossary|""" +
                """acknowledge?ments))$LABEL_END""",
        )

    fun parse(text: String): Result {
        val lines = text.lines().mapNotNull(::lineOf)
        return when {
            lines.isEmpty() -> Result.Empty
            lines.size > MAX_ITEMS -> Result.TooManyItems
            lines.any(::tooLong) -> Result.LineTooLong
            else -> treeOf(Lines(lines))?.let(Result::Parsed) ?: Result.TooDeep
        }
    }

    // Null for a line with nothing to show: blank, or a bullet alone.
    private fun lineOf(text: String): Line? {
        val indent = text.takeWhile { it.isWhitespace() }.sumOf(::widthOf)
        // A tab between a number and its title is a space; PostgreSQL text refuses NUL, and no other
        // control character belongs in a title.
        val content =
            text
                .trim()
                .map { if (it.isWhitespace()) ' ' else it }
                .filterNot { it.isISOControl() }
                .joinToString("")
                .replace(BULLET, "")
                .trim()
        if (content.isEmpty()) return null
        val label = labelOf(content, indent)?.takeIf { lengthOf(it.number) <= MAX_NUMBER }
        val title = label?.let { content.substring(it.end).replace(SEPARATOR, "") }.orEmpty()
        // A label too long to be one is part of the title; a label alone on its line is the title, and
        // still places the line as a part or chapter.
        return if (label == null || title.isEmpty()) {
            Line(indent = indent, rank = label?.rank, number = null, title = content, length = lengthOf(text))
        } else {
            Line(indent = indent, rank = label.rank, number = label.number, title = title, length = lengthOf(text))
        }
    }

    private class Label(
        val rank: Int,
        match: MatchResult,
    ) {
        val number = match.groupValues[1].trim()
        val end = match.range.last + 1
    }

    private fun labelOf(
        content: String,
        indent: Int,
    ): Label? {
        val part = PART.find(content)
        val chapter = CHAPTER.find(content)
        val dotted = DOTTED.find(content)
        return when {
            part != null -> Label(PART_RANK, part)

            chapter != null -> Label(CHAPTER_RANK, chapter)

            // An indented bare number, as in "3 Ways to Win", starts a title; its indent places it.
            dotted == null || ('.' !in dotted.value && indent > 0) -> null

            else -> Label(dotted.groupValues[1].count { it == '.' } + 1, dotted)
        }
    }

    private fun tooLong(line: Line): Boolean = line.length > MAX_TITLE

    // As PostgreSQL's char_length counts, by code point.
    private fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

    private fun widthOf(c: Char): Int =
        when (c) {
            '\t' -> TAB_WIDTH
            '　' -> IDEOGRAPHIC_SPACE_WIDTH
            else -> 1
        }

    private class Lines(
        val all: List<Line>,
    ) {
        // Indent says the depth in contents that indent any line; in the rest only numbers say it.
        val flat = all.none { it.indent > 0 }
        val lastNumbered = all.indexOfLast { it.rank != null }
    }

    /** A line's place in the tree, before its key. */
    private data class Placed(
        val parent: Int?,
        val position: Int,
        val depth: Int,
    )

    // Null when a line would sit deeper than MAX_DEPTH.
    private fun treeOf(contents: Lines): List<Item>? {
        val lines = contents.all
        // The ranks of the numbered items still open above the next one: its depth is how many enclose it.
        val open = ArrayDeque<Int>()
        val placed = mutableListOf<Placed>()
        // The last item at each depth, the parent of the next one below it.
        val lastAt = arrayOfNulls<Int>(MAX_DEPTH + 1)
        val children = HashMap<Int?, Int>()
        for ((ord, line) in lines.withIndex()) {
            // No level is skipped: an item is at most one below the one before it.
            val wanted = depthOf(line = line, open = open, contents = contents, placed = placed)
            val depth = wanted.coerceAtMost((placed.lastOrNull()?.depth ?: 0) + 1)
            // Too deep by its place, or by its own number, as a first line of 1.2.3.4.5.6.7 says.
            if (depth > MAX_DEPTH || (line.rank ?: 0) > MAX_DEPTH) return null
            val parent = if (depth == 1) null else lastAt[depth - 1]
            val position = children.getOrDefault(parent, 0)
            children[parent] = position + 1
            lastAt[depth] = ord
            for (deeper in depth + 1..MAX_DEPTH) lastAt[deeper] = null
            placed += Placed(parent = parent, position = position, depth = depth)
        }
        val entries = lines.mapIndexed { ord, line -> ItemKeyPolicy.Entry(line.number, line.title, placed[ord].parent) }
        val keys = ItemKeyPolicy.keysOf(entries)
        return placed.mapIndexed { ord, place ->
            Item(
                ord = ord,
                parent = place.parent,
                position = place.position,
                depth = place.depth,
                number = lines[ord].number,
                title = lines[ord].title,
                key = keys[ord],
            )
        }
    }

    private fun depthOf(
        line: Line,
        open: ArrayDeque<Int>,
        contents: Lines,
        placed: List<Placed>,
    ): Int =
        when {
            line.rank != null -> {
                numberedDepth(open, line.rank)
            }

            line.indent > 0 -> {
                indentedDepth(contents.all, placed, placed.size)
            }

            else -> {
                unindentedDepth(line = line, open = open, contents = contents, ord = placed.size)
            }
        }

    // In flat contents an unnumbered line is a section of the part or chapter above it, or a sibling of
    // the numbered section above it. Anywhere else, or as back matter, it starts again at the top.
    private fun unindentedDepth(
        line: Line,
        open: ArrayDeque<Int>,
        contents: Lines,
        ord: Int,
    ): Int {
        val backMatter = ord > contents.lastNumbered && BACK_MATTER.containsMatchIn(line.title)
        if (!contents.flat || open.isEmpty() || backMatter) {
            open.clear()
            return 1
        }
        return if (open.last() > CHAPTER_RANK) open.size else open.size + 1
    }

    // Closes what a rank at or below this one opened, so a chapter after a chapter is its sibling
    // whether or not a part came before them.
    private fun numberedDepth(
        open: ArrayDeque<Int>,
        rank: Int,
    ): Int {
        while (open.isNotEmpty() && open.last() >= rank) open.removeLast()
        open.addLast(rank)
        return open.size
    }

    // An unnumbered line indented past an earlier one is that line's child.
    private fun indentedDepth(
        lines: List<Line>,
        placed: List<Placed>,
        ord: Int,
    ): Int {
        val above = (ord - 1 downTo 0).firstOrNull { lines[it].indent < lines[ord].indent }
        return above?.let { placed[it].depth + 1 } ?: 1
    }
}
