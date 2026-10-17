package gloss.domain.setup.service

import gloss.common.error.GlossException
import gloss.domain.book.service.BookSourceService
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.ToolArgumentsParser
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.llm.domain.chat.ToolDefinition
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.BooksShown
import gloss.domain.setup.domain.CitedPage
import gloss.domain.setup.domain.FoundEdition
import gloss.domain.setup.domain.RecommendationCheck
import gloss.domain.setup.domain.Recommended
import gloss.domain.setup.domain.RecommendedBook
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.SetupTurnPolicy
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.WebSearched
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Runs the tools a model asks for in a setup turn: a book search on YES24, a web search on Exa, and the
 * two that answer the reader, a recommendation and a list of editions. Each answers the model with
 * text and gives what the session keeps; a call that cannot run is refused with a reason the model
 * reads, never thrown.
 */
@Service
class SetupTools(
    private val bookSourceService: BookSourceService,
    private val setupWebSearch: SetupWebSearch,
    objectMapper: ObjectMapper,
) {
    private val transcript = SetupTranscript(objectMapper)

    /** The same list every call while the operator's key stays as it is, so the prompt's prefix does too. */
    val offered: List<ToolDefinition>
        get() = if (setupWebSearch.available) WITH_WEB else WITHOUT_WEB

    /** A session's kept messages as the model reads them. */
    fun transcript(contents: List<SetupContent>): List<ChatMessage> = transcript.of(contents)

    /** What the model is told, what the session keeps, and whether the reader has their answer. */
    data class Outcome(
        val reply: String,
        val kept: SetupContent,
        val answers: Boolean = false,
    )

    /** [alone] says whether the model's call asked for answering tools only, as one must be to run. */
    fun run(
        call: ToolCall,
        turn: SetupTurn,
        alone: Boolean,
    ): Outcome {
        val tool =
            SetupTurnPolicy.Tool.of(call.name)?.takeIf { tool -> offered.any { it.name == tool.code } }
                ?: return refuse(call, "there is no tool named ${call.name}")
        if (turn.answered) return refuse(call, "the reader already has an answer in this turn")
        if (tool.answers && !alone) return refuse(call, MIXED)
        if (!SetupTurnPolicy.admits(
                tool,
                turn.used(tool),
            )
        ) {
            return refuse(call, "no more ${tool.code} calls in this turn")
        }
        turn.count(tool)
        val arguments =
            when (val parsed = call.parsedArguments()) {
                is ToolArgumentsParser.Result.Parsed -> parsed.values
                is ToolArgumentsParser.Result.Malformed -> return refuse(call, "the arguments are not a JSON object")
            }
        return when (tool) {
            SetupTurnPolicy.Tool.FIND_BOOKS -> findBooks(call, arguments, turn)
            SetupTurnPolicy.Tool.SEARCH_WEB -> searchWeb(call, arguments, turn)
            SetupTurnPolicy.Tool.RECOMMEND -> recommend(call, arguments, turn)
            SetupTurnPolicy.Tool.SHOW_BOOKS -> showBooks(call, arguments, turn)
        }
    }

    private fun findBooks(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: SetupTurn,
    ): Outcome {
        val query =
            textOf(arguments["query"], MAX_BOOK_QUERY) ?: return refuse(call, "query must be 1 to 100 characters")
        val editions =
            try {
                bookSourceService.editions(turn.accountId, query)
            } catch (e: GlossException) {
                return refuse(call, "the book search failed: ${e.error.code}")
            }
        val found = BooksFound(query, editions.map { FoundEdition(it.ref, it.hasContents) }, call.id)
        turn.found(found)
        return Outcome(reply = transcript.foundBooks(editions), kept = found)
    }

    private fun searchWeb(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: SetupTurn,
    ): Outcome {
        val query =
            textOf(arguments["query"], MAX_WEB_QUERY) ?: return refuse(call, "query must be 1 to 200 characters")
        val results =
            when (val searched = setupWebSearch.search(turn.accountId, turn.sessionId, query)) {
                is SetupWebSearch.Searched.Found -> searched.results
                is SetupWebSearch.Searched.Refused -> return refuse(call, "${searched.problem}; use find_books")
            }
        results.pages.forEach(turn::read)
        return Outcome(
            reply = transcript.foundPages(results.pages),
            kept = WebSearched(callId = call.id, query = query, results = results.pages.size),
        )
    }

    private fun recommend(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: SetupTurn,
    ): Outcome =
        when (val checked = RecommendationCheck.check(arguments, turn.offered(), turn.pages())) {
            is RecommendationCheck.Result.Refused -> {
                refuse(call, checked.problem)
            }

            is RecommendationCheck.Result.Accepted -> {
                val books =
                    checked.picks.map { pick ->
                        RecommendedBook(
                            ref = pick.ref,
                            reason = pick.reason,
                            sources = pick.sources.map { CitedPage(url = it.url, title = it.title) },
                        )
                    }
                turn.answer()
                Outcome(
                    reply = SetupTranscript.SHOWN,
                    kept = Recommended(callId = call.id, intro = checked.intro, books = books),
                    answers = true,
                )
            }
        }

    private fun showBooks(
        call: ToolCall,
        arguments: Map<String, Any?>,
        turn: SetupTurn,
    ): Outcome {
        val query =
            textOf(arguments["query"], MAX_BOOK_QUERY) ?: return refuse(call, "query must be 1 to 100 characters")
        val found =
            turn.searchedFor(query) ?: return refuse(call, "no find_books call in this turn searched for that query")
        if (found.books.isEmpty()) return refuse(call, "that search found nothing; tell the reader in words")
        turn.answer()
        return Outcome(
            reply = SetupTranscript.SHOWN,
            kept = BooksShown(callId = call.id, booksCallId = checkNotNull(found.callId)),
            answers = true,
        )
    }

    private fun refuse(
        call: ToolCall,
        problem: String,
    ) = Outcome(reply = transcript.refused(problem), kept = ToolRefused(call.id, problem))

    private fun textOf(
        value: Any?,
        max: Int,
    ): String? =
        (value as? String)
            ?.trim()
            ?.takeIf { text ->
                text.codePointCount(0, text.length) in 1..max && text.none { it.isISOControl() }
            }

    private companion object {
        const val MIXED = "recommend and show_books end the turn: call one alone, after the searches it needs"

        // As the book source takes them.
        const val MAX_BOOK_QUERY = 100

        // Exa states no maximum (SearchRequest.query has only minLength 1); past this the model is
        // pasting text, not describing a page, and the query is repeated in the prompt and kept.
        const val MAX_WEB_QUERY = 200

        val FIND_BOOKS =
            ToolDefinition(
                name = SetupTurnPolicy.Tool.FIND_BOOKS.code,
                description = SetupPrompt.FIND_BOOKS_DESCRIPTION,
                parameters = SetupPrompt.FIND_BOOKS_PARAMETERS,
            )
        val SEARCH_WEB =
            ToolDefinition(
                name = SetupTurnPolicy.Tool.SEARCH_WEB.code,
                description = SetupPrompt.SEARCH_WEB_DESCRIPTION,
                parameters = SetupPrompt.SEARCH_WEB_PARAMETERS,
            )
        val RECOMMEND =
            ToolDefinition(
                name = SetupTurnPolicy.Tool.RECOMMEND.code,
                description = SetupPrompt.RECOMMEND_DESCRIPTION,
                parameters = SetupPrompt.RECOMMEND_PARAMETERS,
            )
        val SHOW_BOOKS =
            ToolDefinition(
                name = SetupTurnPolicy.Tool.SHOW_BOOKS.code,
                description = SetupPrompt.SHOW_BOOKS_DESCRIPTION,
                parameters = SetupPrompt.SHOW_BOOKS_PARAMETERS,
            )
        val WITH_WEB = listOf(FIND_BOOKS, SEARCH_WEB, RECOMMEND, SHOW_BOOKS)
        val WITHOUT_WEB = listOf(FIND_BOOKS, RECOMMEND, SHOW_BOOKS)
    }
}
