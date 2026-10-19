package gloss.domain.chat.domain.repository

import gloss.domain.chat.domain.ChatSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ChatSessionRepository : JpaRepository<ChatSession, UUID> {
    fun findByIdAndAccountIdAndBookId(
        id: UUID,
        accountId: UUID,
        bookId: UUID,
    ): ChatSession?

    // Held until commit, so messages are numbered in turn and a conversation is not deleted while a turn opens.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('chat:' || cast(:bookId as text), 0))",
        nativeQuery = true,
    )
    fun lockBook(bookId: UUID): Int
}
