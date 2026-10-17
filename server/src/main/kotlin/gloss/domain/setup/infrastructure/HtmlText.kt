package gloss.domain.setup.infrastructure

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * A page's text as a browser would show it, one line for each heading, paragraph, list item or row,
 * so contents laid out as a list read as one item a line. Parsed by jsoup, in time linear in the page,
 * never by patterns over its markup: the page is anyone's. Scripts, styles, the head, comments and
 * hidden elements are dropped; nothing of the markup is kept or run.
 */
internal object HtmlText {
    private const val MAX_TITLE = 300

    private const val HIDDEN = "script, style, noscript, template, svg, head, [hidden], [aria-hidden=true]"

    // Where a browser starts a new line.
    private val BLOCKS =
        setOf(
            "br",
            "hr",
            "p",
            "div",
            "li",
            "ul",
            "ol",
            "dl",
            "dt",
            "dd",
            "tr",
            "table",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "section",
            "article",
            "header",
            "footer",
            "nav",
            "main",
            "aside",
            "blockquote",
            "pre",
            "summary",
            "details",
            "figure",
            "figcaption",
        )

    // Cells of a row, as a browser sets them apart.
    private val CELLS = setOf("td", "th")
    private val SPACES = Regex("""[ \t\u00A0\u200B\f\r]+""")

    /** A page parsed once, for its title and its text. */
    class Page(
        html: String,
    ) {
        private val document: Document = Jsoup.parse(html)

        val title: String? =
            document
                .title()
                .replace(SPACES, " ")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.take(MAX_TITLE)

        val text: String = textOf(document)
    }

    fun of(html: String): String = Page(html).text

    fun titleOf(html: String): String? = Page(html).title

    /** Plain text with its blank lines and runs of spaces taken out. */
    fun plain(text: String): String =
        text
            .lines()
            .map { it.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    // Walked without recursion, so a page nested a thousand deep reads as any other.
    private fun textOf(document: Document): String {
        document.select(HIDDEN).remove()
        val out = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(
                    node: Node,
                    depth: Int,
                ) {
                    when {
                        node is TextNode -> out.append(node.text())
                        node is Element && node.normalName() in BLOCKS -> out.append('\n')
                        node is Element && node.normalName() in CELLS -> out.append(' ')
                    }
                }

                override fun tail(
                    node: Node,
                    depth: Int,
                ) {
                    if (node is Element && node.normalName() in BLOCKS) out.append('\n')
                }
            },
            document.body(),
        )
        return plain(out.toString())
    }
}
