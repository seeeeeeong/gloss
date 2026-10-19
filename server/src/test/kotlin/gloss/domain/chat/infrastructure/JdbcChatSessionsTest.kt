package gloss.domain.chat.infrastructure

import gloss.TestcontainersConfiguration
import gloss.domain.chat.domain.ChatLine
import gloss.domain.chat.domain.ChatRole
import gloss.domain.chat.domain.ChatSession
import gloss.domain.chat.domain.ChatStatus
import gloss.domain.chat.domain.repository.ChatSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class JdbcChatSessionsTest(
    @Autowired private val jdbcChatSessions: JdbcChatSessions,
    @Autowired private val jdbcChatLines: JdbcChatLines,
    @Autowired private val chatSessionRepository: ChatSessionRepository,
    @Autowired private val jdbc: JdbcClient,
) {
    private val now = Instant.parse("2026-10-17T00:00:00Z")

    private class Shelf(
        val account: UUID,
        val book: UUID,
    )

    private fun shelf(): Shelf {
        val account = UUID.randomUUID()
        val book = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", account)
            .param("loginId", "q-" + account.toString().take(12))
            .update()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at)
                values (:id, 0, :account, '책', '{}', 'manual', now())
                """.trimIndent(),
            ).param("id", book)
            .param("account", account)
            .update()
        return Shelf(account, book)
    }

    private fun session(
        shelf: Shelf,
        at: Instant,
    ): ChatSession {
        val session = ChatSession.start(accountId = shelf.account, bookId = shelf.book, now = at)
        chatSessionRepository.save(session)
        chatSessionRepository.flush()
        return session
    }

    private data class Asked(
        val seq: Int,
        val text: String,
        val itemKey: String?,
        val at: Instant,
    )

    private fun turn(
        session: ChatSession,
        asked: Asked,
    ) {
        val reader =
            ChatLine(
                seq = asked.seq,
                role = ChatRole.READER,
                itemKey = asked.itemKey,
                text = asked.text,
                createdAt = asked.at,
                asked =
                    ChatLine.Asked(
                        turnId = UUID.randomUUID(),
                        quote = null,
                        prompt = "질문: ${asked.text}",
                        postVersionId = null,
                        planId = UUID.randomUUID(),
                        historyFrom = 1,
                    ),
            )
        val reply =
            ChatLine(
                seq = asked.seq + 1,
                role = ChatRole.GLOSS,
                itemKey = asked.itemKey,
                text = "답",
                createdAt = asked.at.plusSeconds(1),
                answered =
                    ChatLine.Answered(
                        replyTo = asked.seq,
                        status = ChatStatus.DONE,
                        finishReason = "stop",
                        errorCode = null,
                        model = "deepseek-flash",
                        promptVersion = "tutor-1",
                        usage = null,
                        reservationId = null,
                    ),
            )
        jdbcChatLines.append(session, reader)
        jdbcChatLines.append(session, reply)
    }

    @Test
    fun `a book's conversations are listed by their newest message, each by its first question`() {
        val shelf = shelf()
        val older = session(shelf, now)
        val newer = session(shelf, now.plus(Duration.ofHours(1)))
        turn(older, Asked(seq = 1, text = "처음", itemKey = "1.1", at = now))
        turn(newer, Asked(seq = 1, text = "표지에서", itemKey = null, at = now.plus(Duration.ofHours(1))))
        turn(older, Asked(seq = 3, text = "이어서", itemKey = "2", at = now.plus(Duration.ofHours(2))))

        val listed = jdbcChatSessions.list(shelf.account, shelf.book)

        assertThat(listed.map { it.id }).containsExactly(older.id, newer.id)
        val first = listed.first()
        assertThat(first.firstQuestion).isEqualTo("처음")
        assertThat(first.itemKey).isEqualTo("1.1")
        assertThat(first.itemKeys).containsExactlyInAnyOrder("1.1", "2")
        assertThat(first.startedAt).isEqualTo(now)
        assertThat(first.lastActivityAt).isEqualTo(now.plus(Duration.ofHours(2)).plusSeconds(1))
        assertThat(listed.last().itemKey).isNull()
        assertThat(listed.last().itemKeys).isEmpty()
        assertThat(jdbcChatSessions.latest(shelf.account, shelf.book)).isEqualTo(older.id)
    }

    @Test
    fun `a reader with no question asked has no conversation to list`() {
        val shelf = shelf()

        assertThat(jdbcChatSessions.list(shelf.account, shelf.book)).isEmpty()
        assertThat(jdbcChatSessions.latest(shelf.account, shelf.book)).isNull()
    }
}
