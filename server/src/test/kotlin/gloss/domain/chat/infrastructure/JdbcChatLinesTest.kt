package gloss.domain.chat.infrastructure

import gloss.TestcontainersConfiguration
import gloss.domain.chat.domain.ChatHistoryPolicy
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
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class JdbcChatLinesTest(
    @Autowired private val jdbcChatLines: JdbcChatLines,
    @Autowired private val chatSessionRepository: ChatSessionRepository,
    @Autowired private val jdbc: JdbcClient,
) {
    private val now = Instant.parse("2026-10-17T00:00:00Z")

    private fun session(): ChatSession {
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
            .param("loginId", "j-" + account.toString().take(12))
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
        val session = ChatSession.start(accountId = account, bookId = book, now = now)
        chatSessionRepository.save(session)
        chatSessionRepository.flush()
        return session
    }

    private data class Asked(
        val seq: Int,
        val plan: UUID,
        val from: Int,
        val status: ChatStatus = ChatStatus.DONE,
    )

    private fun turn(
        session: ChatSession,
        asked: Asked,
    ) {
        val reader =
            ChatLine(
                seq = asked.seq,
                role = ChatRole.READER,
                itemKey = null,
                text = "질문 ${asked.seq}",
                createdAt = now,
                asked =
                    ChatLine.Asked(
                        turnId = UUID.randomUUID(),
                        quote = null,
                        prompt = "p${asked.seq}",
                        postVersionId = null,
                        planId = asked.plan,
                        historyFrom = asked.from,
                    ),
            )
        val reply =
            ChatLine(
                seq = asked.seq + 1,
                role = ChatRole.GLOSS,
                itemKey = null,
                text = "a${asked.seq}",
                createdAt = now,
                answered =
                    ChatLine.Answered(
                        replyTo = asked.seq,
                        status = asked.status,
                        finishReason =
                            if (asked.status ==
                                ChatStatus.FAILED
                            ) {
                                null
                            } else {
                                "stop".takeIf { asked.status == ChatStatus.DONE } ?: "length"
                            },
                        errorCode = "llm.provider_failed".takeIf { asked.status == ChatStatus.FAILED },
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
    fun `a turn reads only the finished turns from its start under its plan, however many came before`() {
        val session = session()
        val old = UUID.randomUUID()
        val plan = UUID.randomUUID()
        (1..400).forEach { n -> turn(session, Asked(seq = 2 * n - 1, plan = old, from = 1)) }
        (401..410).forEach { n -> turn(session, Asked(seq = 2 * n - 1, plan = plan, from = 801)) }
        turn(session, Asked(seq = 821, plan = plan, from = 805, status = ChatStatus.CUT))
        turn(session, Asked(seq = 823, plan = plan, from = 805))

        val last = jdbcChatLines.last(session.id, session.accountId)
        val read = jdbcChatLines.finished(session, from = 805, planId = plan)

        assertThat(last).isEqualTo(ChatHistoryPolicy.Last(from = 805, planId = plan))
        assertThat(read.map { it.seq }).containsExactly(805, 807, 809, 811, 813, 815, 817, 819, 823)
        assertThat(read.first()).isEqualTo(ChatHistoryPolicy.Exchange(seq = 805, prompt = "p805", reply = "a805"))
        assertThat(jdbcChatLines.lastSeq(session.id, session.accountId)).isEqualTo(824)
    }

    @Test
    fun `a conversation with no question yet has no last turn`() {
        val session = session()

        assertThat(jdbcChatLines.last(session.id, session.accountId)).isNull()
    }
}
