package gloss.domain.chat.service

import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.budget.service.BudgetError
import gloss.domain.budget.service.Purpose
import gloss.domain.chat.domain.ChatHistoryPolicy
import gloss.domain.chat.domain.ChatLine
import gloss.domain.chat.domain.ChatRole
import gloss.domain.chat.domain.ChatSession
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.domain.TutorPrompt
import gloss.domain.chat.domain.repository.ChatSessionRepository
import gloss.domain.chat.dto.request.ChatAskRequest
import gloss.domain.chat.dto.request.ChatStopRequest
import gloss.domain.chat.dto.response.ChatMessageResponse
import gloss.domain.chat.infrastructure.JdbcChatLines
import gloss.domain.llm.domain.chat.ChatMessage
import gloss.domain.llm.domain.chat.Role
import gloss.domain.llm.domain.chat.Thinking
import gloss.domain.llm.service.LlmCall
import gloss.domain.llm.service.LlmError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.util.UUID

/**
 * A turn of a reader's conversation with the tutor about one book: kept before its call goes out,
 * and the reply kept once the call has ended. A question with no conversation named starts a new
 * one. No call is made inside a transaction here.
 */
@Service
class ChatService(
    private val chatSessionRepository: ChatSessionRepository,
    private val jdbcChatLines: JdbcChatLines,
    private val chatSessionService: ChatSessionService,
    private val tutorSources: TutorSources,
    private val chatTurns: ChatTurns,
    private val clock: Clock,
) {
    /**
     * Keeps the reader's message and builds the call that answers it: the rules and the plan, the
     * finished turns as they were sent, then this one, carrying the item's post the first time it
     * goes in. One turn of a conversation is answered at a time.
     */
    @Transactional
    fun open(
        accountId: UUID,
        bookId: UUID,
        request: ChatAskRequest,
    ): Turn {
        val sources = tutorSources.of(accountId, bookId, request.itemKey)
        chatSessionRepository.lockBook(bookId)
        val session =
            request.sessionId?.let { chatSessionService.find(accountId, bookId, it) } ?: start(accountId, bookId)
        val turnId = request.turnId ?: UuidV7.at(clock.instant())
        val stop = chatTurns.start(session.id, accountId, turnId) ?: throw GlossException(ChatError.ANSWERING)
        endUnlessCommitted(session, turnId)
        val next = jdbcChatLines.lastSeq(session.id, accountId) + 1
        val window = windowOf(session, sources.planId, next)
        val post =
            sources.item
                ?.let {
                    tutorSources.post(
                        accountId = accountId,
                        bookId = bookId,
                        item = it,
                        shown = request.postVersionId,
                    )
                }?.takeIf { window.sendsPost(it.versionId) }
        val ask =
            TutorPrompt.Ask(
                item = sources.target,
                post = post?.contentMd,
                quote =
                    request.quote
                        ?.replace(BREAKS, " ")
                        ?.trim()
                        ?.ifEmpty { null },
                question = request.text.trim(),
            )
        val prompt = TutorPrompt.user(ask)
        val asked =
            ChatLine(
                seq = next,
                role = ChatRole.READER,
                itemKey = sources.item?.itemKey,
                text = ask.question,
                createdAt = clock.instant(),
                asked =
                    ChatLine.Asked(
                        turnId = turnId,
                        quote = ask.quote,
                        prompt = prompt,
                        postVersionId = post?.versionId,
                        planId = sources.planId,
                        historyFrom = window.from,
                    ),
            )
        jdbcChatLines.append(session, asked)
        val messages = conversationOf(sources.planJson, window.exchanges, prompt)
        return Turn(session = session, turnId = turnId, asked = asked, call = callOf(accountId, messages), stop = stop)
    }

    /** Keeps how the turn's call ended as Gloss's reply, whatever it was; the reader has seen it. */
    @Transactional
    fun close(
        turn: Turn,
        answer: Answer,
    ): ChatMessageResponse {
        chatSessionRepository.lockBook(turn.session.bookId)
        val reply =
            ChatLine(
                seq = jdbcChatLines.lastSeq(turn.session.id, turn.session.accountId) + 1,
                role = ChatRole.GLOSS,
                itemKey = turn.asked.itemKey,
                text = answer.text,
                createdAt = clock.instant(),
                answered =
                    ChatLine.Answered(
                        replyTo = turn.asked.seq,
                        status = answer.status,
                        finishReason = answer.finishReason,
                        errorCode = answer.errorCode,
                        model = answer.model,
                        promptVersion = TutorPrompt.VERSION,
                        usage = answer.usage,
                        reservationId = answer.reservationId,
                    ),
            )
        jdbcChatLines.append(turn.session, reply)
        return responseOf(reply, turn.session.id)
    }

    /** Stops the turn, now or as soon as it comes; nothing when it has already ended. */
    fun stop(
        accountId: UUID,
        request: ChatStopRequest,
    ) {
        chatTurns.stop(accountId, checkNotNull(request.turnId))
    }

    /** The conversation's turn has ended, however; it no longer holds the conversation. */
    fun end(turn: Turn) {
        chatTurns.end(turn.session.id, turn.session.accountId, turn.turnId)
    }

    private fun start(
        accountId: UUID,
        bookId: UUID,
    ): ChatSession {
        val session = ChatSession.start(accountId = accountId, bookId = bookId, now = clock.instant())
        chatSessionRepository.save(session)
        // Written now, so the message that refers to it finds it.
        chatSessionRepository.flush()
        return session
    }

    // A turn kept is ended by whoever answers it; one never kept must not hold the conversation.
    private fun endUnlessCommitted(
        session: ChatSession,
        turnId: UUID,
    ) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status !=
                        TransactionSynchronization.STATUS_COMMITTED
                    ) {
                        chatTurns.end(session.id, session.accountId, turnId)
                    }
                }
            },
        )
    }

    // Read from the last turn's start, so a turn reads what the history can hold, never the whole conversation.
    private fun windowOf(
        session: ChatSession,
        planId: UUID,
        next: Int,
    ): ChatHistoryPolicy.Window {
        val last = jdbcChatLines.last(session.id, session.accountId)
        val finished = last?.takeIf { it.planId == planId }?.let { jdbcChatLines.finished(session, it.from, planId) }
        return ChatHistoryPolicy.window(last = last, planId = planId, finished = finished.orEmpty(), next = next)
    }

    private fun conversationOf(
        planJson: String,
        exchanges: List<ChatHistoryPolicy.Exchange>,
        prompt: String,
    ): List<ChatMessage> =
        listOf(ChatMessage(Role.SYSTEM, TutorPrompt.system(planJson))) +
            exchanges.flatMap { listOf(ChatMessage(Role.USER, it.prompt), ChatMessage(Role.ASSISTANT, it.reply)) } +
            ChatMessage(Role.USER, prompt)

    // Thinking is off: it bills its reasoning as output, and an answer of a few sentences needs none.
    private fun callOf(
        accountId: UUID,
        messages: List<ChatMessage>,
    ) = LlmCall(
        accountId = accountId,
        purpose = Purpose.CHAT,
        messages = messages,
        maxTokens = TutorPrompt.MAX_TOKENS,
        thinking = Thinking.DISABLED,
    )

    private companion object {
        val BREAKS = Regex("""\s+""")
    }
}

private const val NO_ANSWER = "답을 받지 못했어요. 다시 물어 주세요."

// A reply keeps only the code of what stopped it; the reader is told in that error's own words.
private val KNOWN: List<ErrorCode> = LlmError.entries + BudgetError.entries + ChatError.entries

/** A message of the conversation [sessionId] as the reader sees it; a failed reply in its error's words. */
internal fun responseOf(
    line: ChatLine,
    sessionId: UUID,
): ChatMessageResponse {
    val failed = line.answered?.takeIf { it.status == ChatStatus.FAILED }
    val message = failed?.let { answered -> KNOWN.firstOrNull { it.code == answered.errorCode }?.message ?: NO_ANSWER }
    return ChatMessageResponse.from(line, sessionId, message)
}
