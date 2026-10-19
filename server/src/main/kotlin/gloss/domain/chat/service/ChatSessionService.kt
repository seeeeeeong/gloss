package gloss.domain.chat.service

import gloss.common.error.GlossException
import gloss.domain.chat.domain.ChatSession
import gloss.domain.chat.domain.repository.ChatSessionRepository
import gloss.domain.chat.dto.response.ChatResponse
import gloss.domain.chat.dto.response.ChatSessionResponse
import gloss.domain.chat.infrastructure.JdbcChatLines
import gloss.domain.chat.infrastructure.JdbcChatSessions
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** A reader's conversations about a book: listed, each read whole, and one deleted whole. */
@Service
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val jdbcChatLines: JdbcChatLines,
    private val jdbcChatSessions: JdbcChatSessions,
    private val chatTurns: ChatTurns,
) {
    /** The conversation with the newest activity, or none before the first question. */
    @Transactional(readOnly = true)
    fun latest(
        accountId: UUID,
        bookId: UUID,
    ): ChatResponse {
        val sessionId = jdbcChatSessions.latest(accountId, bookId) ?: return ChatResponse(null, emptyList())
        return conversation(accountId, bookId, sessionId)
    }

    @Transactional(readOnly = true)
    fun conversation(
        accountId: UUID,
        bookId: UUID,
        sessionId: UUID,
    ): ChatResponse {
        val session = find(accountId, bookId, sessionId)
        val lines = jdbcChatLines.list(session.id, accountId)
        return ChatResponse(
            sessionId = session.id,
            messages = lines.map { responseOf(it, session.id) },
            answering = chatTurns.isLive(session.id),
        )
    }

    /** Newest activity first. */
    @Transactional(readOnly = true)
    fun sessions(
        accountId: UUID,
        bookId: UUID,
    ): List<ChatSessionResponse> = jdbcChatSessions.list(accountId, bookId).map(ChatSessionResponse::from)

    /**
     * Deletes the conversation with its messages; refused while a turn of it is under way, which
     * holds its reservation until the reply is kept. What its calls cost stays in the ledger.
     */
    @Transactional
    fun delete(
        accountId: UUID,
        bookId: UUID,
        sessionId: UUID,
    ) {
        chatSessionRepository.lockBook(bookId)
        val session = find(accountId, bookId, sessionId)
        if (chatTurns.isLive(session.id)) throw GlossException(ChatError.STILL_ANSWERING)
        chatSessionRepository.delete(session)
    }

    /** The reader's own conversation about the book; not found for anyone else's. */
    fun find(
        accountId: UUID,
        bookId: UUID,
        sessionId: UUID,
    ): ChatSession =
        chatSessionRepository.findByIdAndAccountIdAndBookId(sessionId, accountId, bookId)
            ?: throw GlossException(ChatError.SESSION_NOT_FOUND)
}
