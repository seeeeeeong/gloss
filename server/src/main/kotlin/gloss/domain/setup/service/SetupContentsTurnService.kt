package gloss.domain.setup.service

import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.common.util.traceOf
import gloss.domain.book.service.BookSourceService
import gloss.domain.book.service.ContentsReader
import gloss.domain.book.service.OutlineItem
import gloss.domain.book.service.SourcedEdition
import gloss.domain.budget.service.Purpose
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.FinishReason
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.domain.chat.ToolChoice
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmReply
import gloss.domain.llm.service.LlmService
import gloss.domain.setup.domain.CalledTool
import gloss.domain.setup.domain.ContentsTurnPolicy
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupPrompt
import gloss.domain.setup.domain.SetupSession
import gloss.domain.setup.domain.ToolsCalled
import gloss.domain.setup.domain.repository.SetupSessionRepository
import gloss.domain.setup.infrastructure.JdbcSourcePages
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Looks on the web for a held edition's detailed contents, with the model on the reader's key: each
 * call is reserved and settled by [LlmService], and its tools run between calls. Contents that pass
 * [gloss.domain.setup.domain.TocSourceCheck] replace the held ones, with the page they were found on;
 * otherwise the bookshop's stay held. A conversation of its own, never read again by a later turn.
 */
@Service
class SetupContentsTurnService(
    private val setupSessionRepository: SetupSessionRepository,
    private val bookSourceService: BookSourceService,
    private val llmService: LlmService,
    private val setupContentsTools: SetupContentsTools,
    private val setupTurnWriter: SetupTurnWriter,
    private val jdbcSourcePages: JdbcSourcePages,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** However it ends, the session stops waiting: with the contents found, or with why there are none. */
    @Suppress("TooGenericExceptionCaught")
    fun seek(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
    ) {
        val asked = TurnAsked(accountId, sessionId, since)
        val ending =
            try {
                look(asked)
            } catch (_: TurnSuperseded) {
                return
            } catch (e: GlossException) {
                failure(endingOf(e.error))
            } catch (e: RuntimeException) {
                log.warn("A contents search failed{}", traceOf(e))
                failure(SetupError.CONTENTS_NOT_FOUND)
            }
        try {
            if (ending != null) setupTurnWriter.keep(asked, listOf(ending), done = true)
        } catch (_: TurnSuperseded) {
            // The session moved on while this one looked.
        }
    }

    /** The last thing to keep, or null when the contents found already ended the turn. */
    private fun look(asked: TurnAsked): SetupContent? {
        val session =
            setupSessionRepository.findByIdAndAccountId(asked.sessionId, asked.accountId)
                ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
        val ref = session.editionRef ?: throw GlossException(SetupError.CONTENTS_NOT_FOUND)
        val edition = bookSourceService.fetch(asked.accountId, ref)
        val known = edition.contents?.let(ContentsReader::read).orEmpty()
        val held = ContentsTurn.Held(ref = ref, title = edition.title, revision = session.revision)
        val turn = ContentsTurn(asked = asked, edition = held, known = known)
        val messages =
            mutableListOf(
                ChatMessage(Role.SYSTEM, SetupPrompt.CONTENTS_SYSTEM),
                ChatMessage(Role.USER, bookOf(edition, known)),
            )
        var call = 1
        while (step(turn, messages, ContentsTurnPolicy.choiceOf(call, turn.read))) call++
        return if (turn.found != null) null else failure(SetupError.CONTENTS_NOT_FOUND)
    }

    /**
     * One model call and the tools it asked for; false once the turn is over. Words are the model
     * saying it found nothing, and any other ending than tool calls gives nothing to use.
     */
    private fun step(
        turn: ContentsTurn,
        messages: MutableList<ChatMessage>,
        choice: ContentsTurnPolicy.Choice,
    ): Boolean {
        if (choice == ContentsTurnPolicy.Choice.STOP) return false
        val reply = ask(turn.asked, messages, choice)
        if (reply.finishReason != FinishReason.TOOL_CALLS || reply.toolCalls.isEmpty()) return false
        messages += runTools(turn, reply)
        return turn.found == null && choice != ContentsTurnPolicy.Choice.SUBMIT
    }

    // Thinking off: it bills output tokens on every call, DeepSeek refuses a forced tool with it on,
    // and with tools offered every earlier turn's reasoning would have to be sent back.
    private fun ask(
        asked: TurnAsked,
        messages: List<ChatMessage>,
        choice: ContentsTurnPolicy.Choice,
    ): LlmReply {
        setupTurnWriter.checkDeadline(asked)
        val call =
            LlmCall(
                accountId = asked.accountId,
                purpose = Purpose.SETUP,
                // A copy: the turn's own list grows after the call.
                messages = messages.toList(),
                maxTokens = ContentsTurnPolicy.MAX_TOKENS,
                thinking = Thinking.DISABLED,
                tools = setupContentsTools.offered,
                toolChoice =
                    if (choice == ContentsTurnPolicy.Choice.SUBMIT) {
                        ToolChoice.Named(ContentsTurnPolicy.Tool.SUBMIT_TOC.code)
                    } else {
                        ToolChoice.Auto
                    },
            )
        val reply = llmService.stream(call, onText = {}, isCancelled = { setupTurnWriter.pastDeadline(asked) })
        if (reply.cancelled) throw GlossException(SetupError.TURN_TIMED_OUT)
        return reply
    }

    /**
     * Runs every call the model made, in its order, and keeps them with their answers in one write; with
     * contents found, the same write holds them and names their page, if the held edition is still the
     * one looked for.
     */
    private fun runTools(
        turn: ContentsTurn,
        reply: LlmReply,
    ): List<ChatMessage> {
        val outcomes =
            reply.toolCalls.map { call ->
                setupTurnWriter.checkDeadline(turn.asked)
                setupContentsTools.run(call, turn)
            }
        val called = ToolsCalled(reply.text, reply.toolCalls.map { CalledTool(it.id, it.name, it.arguments) })
        val found = turn.found
        setupTurnWriter.keep(
            turn.asked,
            listOf(called) + outcomes.map { it.kept },
            done = found != null,
        ) { session, now ->
            if (found != null) hold(session = session, turn = turn, found = found, now = now)
        }
        val answers = reply.toolCalls.zip(outcomes) { call, outcome -> ChatMessage.toolResult(call.id, outcome.reply) }
        return listOf(reply.asAssistantTurn()) + answers
    }

    private fun hold(
        session: SetupSession,
        turn: ContentsTurn,
        found: ContentsTurn.Found,
        now: Instant,
    ) {
        if (session.editionRef != turn.edition.ref || session.revision != turn.edition.revision) throw TurnSuperseded()
        val page = found.page
        val id = UuidV7.at(now)
        val read =
            JdbcSourcePages.Read(
                url = page.finalUrl,
                title = page.title,
                text = page.text,
                fetchedAt = page.readAt,
            )
        jdbcSourcePages.add(session = session, page = read, id = id)
        session.found(contents = found.contents, sourceId = id, at = now)
    }

    // A search that ran out of time found nothing; the reader's key, limit or the bookshop say their own.
    private fun endingOf(error: ErrorCode): ErrorCode =
        if (error == SetupError.TURN_TIMED_OUT) SetupError.CONTENTS_NOT_FOUND else error

    private fun failure(error: ErrorCode) = SearchFailed(error.code, error.message)

    private companion object {
        // Enough to tell the edition apart; a part list longer than this is not what is looked for.
        const val MAX_ANCHORS_SHOWN = 60

        /** The book and the top of the bookshop's contents, in the same words every time for the same book. */
        fun bookOf(
            edition: SourcedEdition,
            known: List<OutlineItem>,
        ): String {
            val top = known.filter { it.depth == 1 }.take(MAX_ANCHORS_SHOWN)
            val lines =
                buildList {
                    add("Book: ${edition.title}")
                    if (edition.authors.isNotEmpty()) add("Authors: ${edition.authors.joinToString(", ")}")
                    val published = listOfNotNull(edition.publisher, edition.publishedOn?.year?.toString())
                    if (published.isNotEmpty()) add("Publisher: ${published.joinToString(", ")}")
                    if (known.isEmpty()) {
                        add("YES24 has no table of contents for it.")
                    } else {
                        add("YES24's table of contents, top level (${known.size} items in all):")
                        top.forEach { add(listOfNotNull(it.number, it.title).joinToString(" ")) }
                    }
                }
            return lines.joinToString("\n")
        }
    }
}
