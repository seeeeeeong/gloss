package gloss.domain.setup.service

import gloss.common.error.GlossException
import gloss.domain.book.service.ContentsReader
import gloss.domain.llm.domain.chat.ToolArgumentsParser
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolDefinition
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.ContentsTurnPolicy
import gloss.domain.setup.domain.PageFetchFailed
import gloss.domain.setup.domain.PageFetched
import gloss.domain.setup.domain.PageGateway
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.TocSourceCheck
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.WebSearched
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URISyntaxException
import java.time.Clock

/**
 * Runs the tools a model asks for while looking for an edition's contents: a web search on Exa, a page
 * read from the server, and the one that answers, the contents submitted, which are checked against
 * the page they cite before they are taken. A call that cannot run is refused with a reason the model
 * reads, never thrown.
 */
@Service
class SetupContentsTools(
    private val setupWebSearch: SetupWebSearch,
    private val pageGateway: PageGateway,
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
) {
    private val transcript = SetupTranscript(objectMapper)

    /** The same list every call, so the prompt's prefix is too. */
    val offered: List<ToolDefinition> = TOOLS

    /** What the model is told, and what the session keeps. */
    data class Outcome(
        val reply: String,
        val kept: SetupContent,
    )

    fun run(
        call: ToolCall,
        turn: ContentsTurn,
    ): Outcome {
        val tool = ContentsTurnPolicy.Tool.of(call.name) ?: return refuse(call, "there is no tool named ${call.name}")
        if (turn.found != null) return refuse(call, "the contents are already submitted")
        if (!ContentsTurnPolicy.admits(tool, turn.used(tool))) {
            return refuse(call, "no more ${tool.code} calls in this turn")
        }
        turn.count(tool)
        val arguments =
            when (val parsed = call.parsedArguments()) {
                is ToolArgumentsParser.Result.Parsed -> parsed.values
                is ToolArgumentsParser.Result.Malformed -> return refuse(call, "the arguments are not a JSON object")
            }
        return when (tool) {
            ContentsTurnPolicy.Tool.SEARCH_WEB -> searchWeb(call, arguments, turn)
            ContentsTurnPolicy.Tool.FETCH_PAGE -> fetchPage(call, arguments, turn)
            ContentsTurnPolicy.Tool.SUBMIT_TOC -> submit(call, arguments, turn)
        }
    }

    private fun searchWeb(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: ContentsTurn,
    ): Outcome {
        val query =
            (arguments["query"] as? String)
                ?.trim()
                ?.takeIf { it.codePointCount(0, it.length) in 1..MAX_WEB_QUERY && it.none(Char::isISOControl) }
                ?: return refuse(call, "query must be 1 to 200 characters")
        val results =
            when (val searched = setupWebSearch.search(turn.asked.accountId, turn.asked.sessionId, query)) {
                is SetupWebSearch.Searched.Found -> searched.results
                is SetupWebSearch.Searched.Refused -> return refuse(call, searched.problem)
            }
        results.pages.forEach(turn::offer)
        return Outcome(
            reply = transcript.foundPages(results.pages),
            kept = WebSearched(callId = call.id, query = query, results = results.pages.size),
        )
    }

    private fun fetchPage(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: ContentsTurn,
    ): Outcome {
        val url = (arguments["url"] as? String)?.trim().orEmpty()
        if (!turn.offered(url)) return refuse(call, "fetch_page reads only a URL search_web returned in this turn")
        val page =
            try {
                pageGateway.fetch(url)
            } catch (e: PageFetchFailed) {
                return refuse(call, e.kind.reason)
            }
        // Read and parsed past the turn's deadline: the turn ends here, not after another call.
        if (!clock.instant().isBefore(turn.asked.deadline)) throw GlossException(SetupError.TURN_TIMED_OUT)
        val text = page.text.take(ContentsTurnPolicy.MAX_PAGE_CHARACTERS)
        if (text.isBlank()) return refuse(call, "the page has no text")
        val title = (page.title ?: hostOf(page.url)).take(MAX_TITLE)
        turn.read(
            ContentsTurn.ReadPage(url = url, finalUrl = page.url, title = title, text = text, readAt = clock.instant()),
        )
        val reply = objectMapper.writeValueAsString(linkedMapOf("url" to page.url, "title" to title, "text" to text))
        return Outcome(
            reply = reply,
            kept = PageFetched(callId = call.id, url = page.url, title = title, characters = text.length),
        )
    }

    private fun submit(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: ContentsTurn,
    ): Outcome {
        val source = (arguments["source_url"] as? String)?.trim().orEmpty()
        val page = turn.page(source) ?: return refuse(call, "source_url must be a page fetch_page read in this turn")
        val lines =
            (arguments["lines"] as? List<*>)
                ?.takeIf { lines -> lines.isNotEmpty() && lines.all { it is String } }
                ?.map { (it as String).trimEnd() }
                ?: return refuse(call, "lines must be a list of the contents' lines")
        val contents = lines.joinToString("\n")
        val items =
            try {
                ContentsReader.read(contents)
            } catch (e: GlossException) {
                return refuse(call, "the contents cannot be read as a table of contents: ${e.error.code}")
            }
        val checked =
            TocSourceCheck.check(
                items = items.map { TocSourceCheck.Item(depth = it.depth, number = it.number, title = it.title) },
                page = page.text,
                bookshop = TocSourceCheck.Bookshop(anchors = turn.anchors, items = turn.known.size),
            )
        if (checked is TocSourceCheck.Result.Refused) return refuse(call, checked.problem)
        turn.find(ContentsTurn.Found(contents = contents, items = items.size, page = page))
        val brought =
            ContentsBrought(
                ref = turn.edition.ref,
                title = turn.edition.title,
                items = items.size,
                typed = false,
                sourceUrl = page.finalUrl,
                sourceTitle = page.title,
            )
        return Outcome(reply = ACCEPTED, kept = brought)
    }

    private fun refuse(
        call: ToolCall,
        problem: String,
    ) = Outcome(reply = transcript.refused(problem), kept = ToolRefused(call.id, problem))

    private fun hostOf(url: String): String =
        try {
            URI(url).host ?: url
        } catch (_: URISyntaxException) {
            url
        }

    private companion object {
        const val ACCEPTED = """{"accepted":true}"""

        // As a recommendation's web search takes it.
        const val MAX_WEB_QUERY = 200

        // As source_page keeps a title.
        const val MAX_TITLE = 300

        val TOOLS =
            listOf(
                ToolDefinition(
                    name = ContentsTurnPolicy.Tool.SEARCH_WEB.code,
                    description = SetupPrompt.CONTENTS_SEARCH_DESCRIPTION,
                    parameters = SetupPrompt.SEARCH_WEB_PARAMETERS,
                ),
                ToolDefinition(
                    name = ContentsTurnPolicy.Tool.FETCH_PAGE.code,
                    description = SetupPrompt.FETCH_PAGE_DESCRIPTION,
                    parameters = SetupPrompt.FETCH_PAGE_PARAMETERS,
                ),
                ToolDefinition(
                    name = ContentsTurnPolicy.Tool.SUBMIT_TOC.code,
                    description = SetupPrompt.SUBMIT_TOC_DESCRIPTION,
                    parameters = SetupPrompt.SUBMIT_TOC_PARAMETERS,
                ),
            )
    }
}
