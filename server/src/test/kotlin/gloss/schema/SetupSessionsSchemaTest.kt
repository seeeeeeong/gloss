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

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class SetupSessionsSchemaTest(
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
            .param("loginId", "u-" + id.toString().take(12))
            .update()
        return id
    }

    private fun bookWithToc(account: UUID): Pair<UUID, UUID> {
        val book = UUID.randomUUID()
        val toc = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at)
                values (:id, 0, :account, '오브젝트', '{}', 'manual', now())
                """.trimIndent(),
            ).param("id", book)
            .param("account", account)
            .update()
        jdbc
            .sql(
                """
                insert into toc (id, version, account_id, book_id, version_no, origin, raw, raw_sha256, status, created_at)
                values (:id, 0, :account, :book, 1, 'manual', '1장', repeat('a', 64), 'draft', now())
                """.trimIndent(),
            ).param("id", toc)
            .param("account", account)
            .param("book", book)
            .update()
        return book to toc
    }

    private data class Session(
        val account: UUID,
        val state: String = "searching",
        val edition: String? = null,
        val title: String? = null,
        val typed: Boolean = false,
        val contents: String? = null,
        val book: UUID? = null,
        val toc: UUID? = null,
        val confirmed: Boolean = false,
    )

    private fun session(session: Session): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, edition_ref, held_title, typed_authors,
                                           contents, book_id, toc_id, created_at, updated_at, confirmed_at)
                values (:id, 0, :account, :state, :edition, :title, case when :typed then '{}'::text[] end, :contents,
                        :book, :toc, now(), now(), case when :confirmed then now() end)
                """.trimIndent(),
            ).param("id", id)
            .param("account", session.account)
            .param("state", session.state)
            .param("edition", session.edition)
            .param("title", session.title)
            .param("typed", session.typed)
            .param("contents", session.contents)
            .param("book", session.book)
            .param("toc", session.toc)
            .param("confirmed", session.confirmed)
            .update()
        return id
    }

    private fun message(
        session: UUID,
        account: UUID,
        seq: Int = 1,
        role: String = "reader",
        kind: String = "text",
        payload: String = """{"text": "오브젝트"}""",
    ) = jdbc
        .sql(
            """
            insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
            values (:id, :session, :account, :seq, :role, :kind, cast(:payload as jsonb), now())
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("session", session)
        .param("account", account)
        .param("seq", seq)
        .param("role", role)
        .param("kind", kind)
        .param("payload", payload)
        .update()

    private fun assertRefused(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun chosen(account: UUID) =
        Session(account = account, state = "book_chosen", edition = "101", title = "오브젝트", contents = "1장")

    @Test
    fun `a session searches, holds a picked edition's contents, then names the book made from them`() {
        val account = account()
        val (book, toc) = bookWithToc(account)

        assertThatCode {
            session(Session(account))
            session(chosen(account))
            session(chosen(account).copy(state = "confirmed", book = book, toc = toc, confirmed = true))
        }.doesNotThrowAnyException()
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a session that is still searching holds no edition`() {
        val account = account()

        assertRefused { session(chosen(account).copy(state = "searching")) }
    }

    @Test
    fun `a picked edition comes with its contents`() {
        val account = account()

        assertRefused { session(chosen(account).copy(contents = null)) }
    }

    @Test
    fun `no book is made before the reader starts`() {
        val account = account()
        val (book, toc) = bookWithToc(account)

        assertRefused { session(chosen(account).copy(book = book, toc = toc)) }
    }

    @Test
    fun `a started session names its book and the version confirmed`() {
        val account = account()
        val (book, _) = bookWithToc(account)

        assertRefused { session(chosen(account).copy(state = "confirmed", book = book, confirmed = true)) }
    }

    @Test
    fun `only a started session says when it started`() {
        val account = account()

        assertRefused { session(chosen(account).copy(confirmed = true)) }
    }

    @Test
    fun `a book typed in is held as an edition is`() {
        val account = account()

        assertThatCode {
            session(Session(account = account, state = "book_chosen", title = "나의 노트", typed = true, contents = "1장"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a session holds an edition or a typed book, never both`() {
        val account = account()

        assertRefused { session(chosen(account).copy(typed = true)) }
    }

    @Test
    fun `held contents come with their title, so the two are read together`() {
        val account = account()

        assertRefused { session(chosen(account).copy(title = null)) }
    }

    @Test
    fun `a typed book is held with its title as a pick is`() {
        val account = account()

        assertRefused { session(Session(account = account, state = "book_chosen", typed = true, contents = "1장")) }
    }

    @Test
    fun `a session is in a known state`() {
        assertRefused { session(Session(account(), state = "styling")) }
    }

    @Test
    fun `held contents are no longer than a book's`() {
        val account = account()

        assertRefused { session(chosen(account).copy(contents = "가".repeat(100_001))) }
    }

    @Test
    fun `a session names a book of its own reader's`() {
        val (book, toc) = bookWithToc(account())
        val account = account()

        assertRefused { session(chosen(account).copy(state = "confirmed", book = book, toc = toc, confirmed = true)) }
    }

    @Test
    fun `a session names contents of the book it made`() {
        val account = account()
        val (book, _) = bookWithToc(account)
        val (_, otherToc) = bookWithToc(account)

        assertRefused {
            session(chosen(account).copy(state = "confirmed", book = book, toc = otherToc, confirmed = true))
        }
    }

    @Test
    fun `messages are numbered once in a session, by either side`() {
        val account = account()
        val session = session(Session(account))
        message(session, account)
        message(
            session = session,
            account = account,
            seq = 2,
            role = "gloss",
            kind = "books",
            payload = """{"query": "오브젝트", "books": []}""",
        )

        assertRefused { message(session, account, seq = 2) }
    }

    @Test
    fun `a message belongs to its session's reader`() {
        val session = session(Session(account()))

        assertRefused { message(session, account()) }
    }

    @Test
    fun `the reader may say they typed contents in`() {
        val account = account()

        assertThatCode {
            message(
                session = session(Session(account)),
                account = account,
                kind = "typed",
                payload = """{"title": "나의 노트"}""",
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `the reader does not speak for Gloss`() {
        val account = account()

        assertRefused { message(session(Session(account)), account, kind = "books") }
    }

    @Test
    fun `Gloss does not speak for the reader`() {
        val account = account()

        assertRefused { message(session = session(Session(account)), account = account, role = "gloss", kind = "text") }
    }

    @Test
    fun `a payload is an object`() {
        val account = account()

        assertRefused { message(session(Session(account)), account, payload = "[]") }
    }

    @Test
    fun `a message is never changed`() {
        val account = account()
        val session = session(Session(account))
        message(session, account)

        assertThatThrownBy {
            jdbc
                .sql("update setup_message set payload = '{}' where session_id = :session")
                .param("session", session)
                .update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }

    @Test
    fun `a message is never deleted`() {
        val account = account()
        val session = session(Session(account))
        message(session, account)

        assertThatThrownBy {
            jdbc.sql("delete from setup_message where session_id = :session").param("session", session).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }
}
