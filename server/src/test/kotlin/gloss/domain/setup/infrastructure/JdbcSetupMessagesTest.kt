package gloss.domain.setup.infrastructure

import gloss.TestcontainersConfiguration
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.FoundEdition
import gloss.domain.setup.domain.PageFetched
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderSought
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupSession
import gloss.domain.setup.domain.repository.SetupSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class JdbcSetupMessagesTest(
    @Autowired private val jdbcSetupMessages: JdbcSetupMessages,
    @Autowired private val setupSessionRepository: SetupSessionRepository,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val clock: Clock,
) {
    private fun session(): SetupSession {
        val account = UUID.randomUUID()
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
        val session = SetupSession.start(accountId = account, now = clock.instant())
        setupSessionRepository.saveAndFlush(session)
        return session
    }

    private val book = FoundEdition(ref = "101", hasContents = true)

    @Test
    fun `messages come back in order, numbered from one, as they were written`() {
        val session = session()
        val now = clock.instant()

        jdbcSetupMessages.append(session, ReaderSaid("오브젝트"), now)
        jdbcSetupMessages.append(session, BooksFound("오브젝트", listOf(book)), now)

        val messages = jdbcSetupMessages.list(session.id, session.accountId)
        assertThat(messages.map { it.seq }).containsExactly(1, 2)
        assertThat(messages.map { it.content }).containsExactly(ReaderSaid("오브젝트"), BooksFound("오브젝트", listOf(book)))
        val kept =
            jdbc
                .sql("select role || ':' || kind from setup_message where session_id = :session order by seq")
                .param("session", session.id)
                .query(String::class.java)
                .list()
        assertThat(kept).containsExactly("reader:text", "gloss:books")
    }

    @Test
    fun `an older session's look for contents on other sites still reads, the page it named left out`() {
        val session = session()
        listOf(
            "reader" to "seek" to """{"ref":"101"}""",
            "gloss" to "page_fetched" to
                """{"callId":"c1","url":"https://example.com/toc","title":"목차","characters":900}""",
            "gloss" to "contents" to
                """{"ref":"101","title":"운영체제","items":206,"typed":false,"thin":false,""" +
                """"sourceUrl":"https://example.com/toc","sourceTitle":"목차"}""",
            "gloss" to "search_failed" to
                """{"code":"setup.contents_not_found","message":"다른 출처에서 목차를 찾지 못했어요."}""",
        ).forEachIndexed { i, (kind, payload) ->
            jdbc
                .sql(
                    """
                    insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
                    values (:id, :session, :account, :seq, :role, :kind, cast(:payload as jsonb), now())
                    """.trimIndent(),
                ).param("id", UUID.randomUUID())
                .param("session", session.id)
                .param("account", session.accountId)
                .param("seq", i + 1)
                .param("role", kind.first)
                .param("kind", kind.second)
                .param("payload", payload)
                .update()
        }

        val messages = jdbcSetupMessages.list(session.id, session.accountId)

        assertThat(messages.map { it.content }).containsExactly(
            ReaderSought(ref = "101"),
            PageFetched(callId = "c1", url = "https://example.com/toc", title = "목차", characters = 900),
            ContentsBrought(ref = "101", title = "운영체제", items = 206, typed = false),
            SearchFailed(code = "setup.contents_not_found", message = "다른 출처에서 목차를 찾지 못했어요."),
        )
    }

    @Test
    fun `another reader reads none of a session's messages`() {
        val session = session()
        jdbcSetupMessages.append(session, ReaderSaid("오브젝트"), clock.instant())

        assertThat(jdbcSetupMessages.list(session.id, UUID.randomUUID())).isEmpty()
    }
}
