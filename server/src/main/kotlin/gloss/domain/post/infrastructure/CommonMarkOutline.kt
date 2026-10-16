package gloss.domain.post.infrastructure

import gloss.domain.post.domain.PostCheck
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.springframework.stereotype.Component

/**
 * A post's top-level blocks, read as the page reads them (CommonMark with GFM tables, raw HTML not
 * rendered): a heading inside code, an HTML block, a list or a quote is no heading of the post's.
 */
@Component
class CommonMarkOutline {
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

    fun of(text: String): List<PostCheck.Block> =
        generateSequence(parser.parse(text).firstChild) { it.next }
            // A link's definition is not shown; only the links that use it are.
            .filterNot { it is LinkReferenceDefinition }
            .map { block ->
                if (block is Heading) {
                    PostCheck.Block.Heading(
                        block.level,
                        textOf(block).trim(),
                    )
                } else {
                    PostCheck.Block.Other
                }
            }.toList()

    // The page shows raw HTML as its source, so a heading's words are what the reader sees, tags and all.
    private fun textOf(node: Node): String =
        when (node) {
            is Text -> node.literal

            is Code -> node.literal

            is HtmlInline -> node.literal

            // The page removes images, alt text and all.
            is Image -> ""

            is SoftLineBreak, is HardLineBreak -> " "

            else -> generateSequence(node.firstChild) { it.next }.joinToString("") { textOf(it) }
        }
}
