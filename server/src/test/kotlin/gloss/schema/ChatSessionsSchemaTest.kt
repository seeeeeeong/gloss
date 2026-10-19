package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ChatSessionsSchemaTest(
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
            .param("loginId", "s-" + id.toString().take(12))
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
        book: UUID,
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

    /** A question and its reply, numbered from [seq]. */
    private fun turn(
        account: UUID,
        session: UUID,
        seq: Int,
    ) {
        jdbc
            .sql(
                """
                insert into chat_message (id, session_id, account_id, seq, role, text, prompt, turn_id, plan_id,
                                          history_from, created_at)
                values (:id, :session, :account, :seq, 'reader', '왜요?', '질문: 왜요?', :turn, :plan, 1, now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("session", session)
            .param("account", account)
            .param("seq", seq)
            .param("turn", UUID.randomUUID())
            .param("plan", UUID.randomUUID())
            .update()
        jdbc
            .sql(
                """
                insert into chat_message (id, session_id, account_id, seq, role, text, reply_to, status,
                                          finish_reason, model, prompt_version, created_at)
                values (:id, :session, :account, :seq, 'gloss', '자원을 나눠요.', :replyTo, 'done', 'stop',
                        'deepseek-flash', 'tutor-1', now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("session", session)
            .param("account", account)
            .param("seq", seq + 1)
            .param("replyTo", seq)
            .update()
    }

    private fun messages(session: UUID): Int =
        jdbc
            .sql("select count(*) from chat_message where session_id = :id")
            .param("id", session)
            .query(Int::class.java)
            .single()

    @Test
    fun `a reader keeps several conversations about a book`() {
        val account = account()
        val book = book(account)
        session(account, book)

        assertThatCode { session(account, book) }.doesNotThrowAnyException()
    }

    @Test
    fun `a conversation is deleted whole, with its messages, and leaves the others`() {
        val account = account()
        val book = book(account)
        val deleted = session(account, book)
        val kept = session(account, book)
        turn(account, deleted, 1)
        turn(account, deleted, 3)
        turn(account, kept, 1)

        jdbc.sql("delete from chat_session where id = :id").param("id", deleted).update()

        assertThat(messages(deleted)).isZero()
        assertThat(messages(kept)).isEqualTo(2)
    }

    @Test
    fun `a message is still never changed`() {
        val account = account()
        val session = session(account, book(account))
        turn(account, session, 1)

        assertThatThrownBy {
            jdbc.sql("update chat_message set text = '고친 답' where session_id = :id").param("id", session).update()
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a message is never deleted apart from its conversation`() {
        val account = account()
        val session = session(account, book(account))
        turn(account, session, 1)

        assertThatThrownBy {
            jdbc
                .sql("delete from chat_message where session_id = :id and seq = 2")
                .param("id", session)
                .update()
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `a conversation's messages are never deleted while it stands`() {
        val account = account()
        val session = session(account, book(account))
        turn(account, session, 1)

        assertThatThrownBy {
            jdbc.sql("delete from chat_message where session_id = :id").param("id", session).update()
        }.isInstanceOf(DataAccessException::class.java)
    }
}
