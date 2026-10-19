package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// V24 lets a reader keep several conversations a book and delete one whole; ChatSessionsSchemaTest has those.
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ChatSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun account(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "c-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(account: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at)
                values (:id, 0, :account, '운영체제', '{}', 'manual', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .update()
        return id
    }

    private fun session(
        account: UUID,
        book: UUID = book(account),
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into chat_session (id, version, account_id, book_id, created_at)
                values (:id, 0, :account, :book, now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("book", book)
            .update()
        return id
    }

    private class Conversation(
        val account: UUID,
        val session: UUID,
    )

    private fun conversation(): Conversation {
        val account = account()
        return Conversation(account, session(account))
    }

    private fun reader(
        at: Conversation,
        seq: Int,
        prompt: String? = "질문: 왜요?",
        plan: UUID? = UUID.randomUUID(),
        historyFrom: Int? = 1,
        turn: UUID? = UUID.randomUUID(),
    ) = jdbc
        .sql(
            """
            insert into chat_message (id, session_id, account_id, seq, role, item_key, quote, text, prompt,
                                      turn_id, plan_id, history_from, created_at)
            values (:id, :session, :account, :seq, 'reader', '1.1', '자원 할당자', '왜요?', :prompt,
                    :turn, :plan, :historyFrom, now())
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("session", at.session)
        .param("account", at.account)
        .param("seq", seq)
        .param("prompt", prompt)
        .param("turn", turn)
        .param("plan", plan)
        .param("historyFrom", historyFrom)
        .update()

    private data class Reply(
        val seq: Int,
        val replyTo: Int?,
        val status: String = "done",
        val finishReason: String? = "stop",
        val errorCode: String? = null,
        val tokens: Long? = 10,
        val completion: Long? = 10,
    )

    private fun gloss(
        at: Conversation,
        reply: Reply,
    ) = jdbc
        .sql(
            """
            insert into chat_message (id, session_id, account_id, seq, role, item_key, text, reply_to, status,
                                      finish_reason, error_code, model, prompt_version, cache_hit_tokens,
                                      cache_miss_tokens, completion_tokens, created_at)
            values (:id, :session, :account, :seq, 'gloss', '1.1', '자원을 나눠요.', :replyTo, :status,
                    :finishReason, :errorCode, 'deepseek-flash', 'tutor-1', :tokens, :tokens, :completion, now())
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("session", at.session)
        .param("account", at.account)
        .param("seq", reply.seq)
        .param("replyTo", reply.replyTo)
        .param("status", reply.status)
        .param("finishReason", reply.finishReason)
        .param("errorCode", reply.errorCode)
        .param("tokens", reply.tokens)
        .param("completion", reply.completion)
        .update()

    private fun refused(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a conversation is about a book of its reader's own`() {
        val book = book(account())

        refused { session(account(), book) }
    }

    @Test
    fun `a question and its reply are kept, each other kind of turn as well`() {
        val at = conversation()

        assertThatCode {
            reader(at, 1)
            gloss(at, Reply(seq = 2, replyTo = 1))
            reader(at, 3)
            gloss(at, Reply(seq = 4, replyTo = 3, status = "cut", finishReason = "length"))
            reader(at, 5)
            gloss(
                at,
                Reply(
                    seq = 6,
                    replyTo = 5,
                    status = "failed",
                    finishReason = null,
                    errorCode = "llm.no_key",
                    tokens = null,
                    completion = null,
                ),
            )
            reader(at, 7)
            gloss(
                at,
                Reply(seq = 8, replyTo = 7, status = "stopped", finishReason = null, tokens = null, completion = null),
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `messages are numbered once in a conversation`() {
        val at = conversation()
        reader(at, 1)

        refused { reader(at, 1) }
    }

    @Test
    fun `a message is never changed`() {
        val at = conversation()
        reader(at, 1)

        assertThatThrownBy {
            jdbc.sql("update chat_message set text = '고친 질문' where session_id = :id").param("id", at.session).update()
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a message is never deleted on its own`() {
        val at = conversation()
        reader(at, 1)

        assertThatThrownBy {
            jdbc.sql("delete from chat_message where session_id = :id").param("id", at.session).update()
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a reader's message keeps the prompt it was sent as`() {
        val at = conversation()

        refused { reader(at, 1, prompt = null) }
    }

    @Test
    fun `a reader's message names the page's turn, once in a conversation`() {
        val at = conversation()
        val turn = UUID.randomUUID()
        reader(at, 1, turn = turn)

        refused { reader(at, 2, turn = turn) }
    }

    @Test
    fun `a reader's message always names its turn`() {
        val at = conversation()

        refused { reader(at, 1, turn = null) }
    }

    @Test
    fun `a reader's message names the plan it was asked under`() {
        val at = conversation()

        refused { reader(at, 1, plan = null) }
    }

    @Test
    fun `a reader's message names where its history starts, no later than itself`() {
        val at = conversation()

        refused { reader(at, 1, historyFrom = 2) }
    }

    @Test
    fun `a reply answers a message`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = null)) }
    }

    @Test
    fun `a reply answers a message that was kept`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = 5)) }
    }

    @Test
    fun `a message has one reply`() {
        val at = conversation()
        reader(at, 1)
        gloss(at, Reply(seq = 2, replyTo = 1))

        refused { gloss(at, Reply(seq = 3, replyTo = 1)) }
    }

    @Test
    fun `only an answer the model finished is done`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = 1, finishReason = "length")) }
    }

    @Test
    fun `a cut answer was not finished`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = 1, status = "cut", finishReason = "stop")) }
    }

    @Test
    fun `a failure says why`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = 1, status = "failed", finishReason = null)) }
    }

    @Test
    fun `a call's usage is kept whole or not at all`() {
        val at = conversation()
        reader(at, 1)

        refused { gloss(at, Reply(seq = 2, replyTo = 1, completion = null)) }
    }
}
