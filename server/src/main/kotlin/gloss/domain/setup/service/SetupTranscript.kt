package gloss.domain.setup.service

import gloss.domain.book.service.Edition
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.PromptSize
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.ToolCall
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.BooksShown
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.GlossSaid
import gloss.domain.setup.domain.HistoryPolicy
import gloss.domain.setup.domain.ReaderPicked
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderSought
import gloss.domain.setup.domain.ReaderTyped
import gloss.domain.setup.domain.Recommended
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.ToolRefused
import gloss.domain.setup.domain.ToolsCalled
import gloss.domain.setup.domain.WebPage
import gloss.domain.setup.domain.WebSearched
import tools.jackson.databind.ObjectMapper

/**
 * A session's conversation as the model reads it, rebuilt from what was kept, the same bytes every
 * time, so a later turn's prompt starts with an earlier one's. What a tool found is told in full only
 * within the turn that asked: afterwards a book search is its refs and a web search its count, since
 * neither source's details are kept. A look for an edition's contents is a conversation of its own and
 * is never read here.
 */
internal class SetupTranscript(
    private val objectMapper: ObjectMapper,
) {
    /**
     * The turn being answered, the last, whole; before it the newest answered turns that fit
     * [HistoryPolicy.BUDGET], and every pick or contents the reader typed in. A turn that failed or
     * never ended is left out, so an error, as a prompt too long, is never read again and never
     * repeats. Readers' messages in a row, as a pick and the question after it, are read as one.
     */
    fun of(contents: List<SetupContent>): List<ChatMessage> {
        val units = unitsOf(contents)
        val current = units.lastOrNull()?.takeIf { it.first() is ReaderSaid }.orEmpty()
        val kept = units.dropLast(if (current.isEmpty()) 0 else 1).filter(::kept).map(::messagesOf)
        val start = HistoryPolicy.start(kept.map(PromptSize::upperBound))
        return joined(kept.drop(start).flatten() + messagesOf(current))
    }

    private fun messagesOf(unit: List<SetupContent>): List<ChatMessage> = unit.mapNotNull(::messageOf)

    private fun messageOf(content: SetupContent): ChatMessage? =
        when (content) {
            is ReaderSaid -> {
                ChatMessage(Role.USER, content.text)
            }

            is ToolsCalled -> {
                ChatMessage(
                    role = Role.ASSISTANT,
                    content = content.text,
                    toolCalls = content.calls.map { ToolCall(id = it.id, name = it.name, arguments = it.arguments) },
                )
            }

            // A search answered before models took part has no call to answer: it is Gloss's answer.
            is BooksFound -> {
                val kept = keptBooks(content)
                content.callId?.let { ChatMessage.toolResult(it, kept) } ?: ChatMessage(Role.ASSISTANT, kept)
            }

            // Fixed words around what is kept, so a later turn reads the same bytes.
            is ReaderPicked -> {
                ChatMessage(Role.USER, "「${content.title}」을(를) 골랐어요.")
            }

            is ReaderTyped -> {
                ChatMessage(Role.USER, "목차를 직접 입력했어요: ${content.title}")
            }

            is WebSearched -> {
                ChatMessage.toolResult(content.callId, json(mapOf("results" to content.results, "pages" to "not kept")))
            }

            is ToolRefused -> {
                ChatMessage.toolResult(content.callId, refused(content.problem))
            }

            is Recommended -> {
                ChatMessage.toolResult(content.callId, SHOWN)
            }

            is BooksShown -> {
                ChatMessage.toolResult(content.callId, SHOWN)
            }

            is GlossSaid -> {
                ChatMessage(Role.ASSISTANT, content.text)
            }

            else -> {
                null
            }
        }

    private fun keptBooks(found: BooksFound): String =
        json(
            mapOf(
                "books" to found.books.map { linkedMapOf("ref" to it.ref, "hasContents" to it.hasContents) },
                "details" to "not kept; search again to read them",
            ),
        )

    /** A book search's editions as the model reads them in the turn that asked. */
    fun foundBooks(editions: List<Edition>): String =
        json(
            mapOf(
                "books" to
                    editions.map {
                        linkedMapOf(
                            "ref" to it.ref,
                            "title" to it.title,
                            "authors" to it.authors,
                            "publisher" to it.publisher,
                            "published" to it.publishedOn?.toString(),
                            "kind" to it.kind,
                            "hasContents" to it.hasContents,
                        )
                    },
            ),
        )

    /** A web search's pages as the model reads them in the turn that asked. */
    fun foundPages(pages: List<WebPage>): String =
        json(
            mapOf(
                "results" to
                    pages.map {
                        linkedMapOf(
                            "url" to it.url,
                            "title" to it.title,
                            "published" to it.published,
                            "highlights" to it.highlights,
                        )
                    },
            ),
        )

    fun refused(problem: String): String = json(mapOf("error" to problem))

    private fun json(value: Any): String = objectMapper.writeValueAsString(value)

    companion object {
        const val SHOWN = """{"shown":true}"""
    }
}

// Each reader message opens a turn, and each pick or typed contents a unit of its own, so a pick
// after a failed turn is still read; so does a look for contents, which is never read. What came
// before the first opens none.
private fun unitsOf(contents: List<SetupContent>): List<List<SetupContent>> {
    val units = mutableListOf<MutableList<SetupContent>>()
    for (content in contents) {
        if (opens(content)) units += mutableListOf<SetupContent>()
        units.lastOrNull()?.add(content)
    }
    return units
}

private fun opens(content: SetupContent): Boolean =
    when (content) {
        is ReaderSaid, is ReaderPicked, is ReaderTyped -> true
        else -> seeks(content)
    }

// Asked by the reader, or started by contents too coarse to write from.
private fun seeks(content: SetupContent) = content is ReaderSought || (content is ContentsBrought && content.thin)

private fun kept(unit: List<SetupContent>): Boolean =
    !seeks(unit.first()) && (unit.first() !is ReaderSaid || unit.any(::answers))

// A search answered before models took part is that turn's answer.
private fun answers(content: SetupContent) =
    content is GlossSaid ||
        content is Recommended ||
        content is BooksShown ||
        (content is BooksFound && content.callId == null)

private fun joined(messages: List<ChatMessage>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    for (message in messages) {
        val last = out.lastOrNull()
        if (message.role == Role.USER && last?.role == Role.USER) {
            out[out.lastIndex] = ChatMessage(Role.USER, last.content + "\n" + message.content)
        } else {
            out += message
        }
    }
    return out
}
