package gloss.domain.setup.service

import gloss.TestcontainersConfiguration
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.book.service.EditionCache
import gloss.domain.setup.TitleSearchModel
import gloss.domain.setup.dto.request.SetupConfirmRequest
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.request.SetupPickRequest
import gloss.domain.setup.dto.response.SetupSessionResponse
import gloss.domain.setup.dto.response.SetupSessionSummaryResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class, TitleSearchModel.Config::class)
@Transactional
class SetupHistoryServiceTest(
    @Autowired private val setupHistoryService: SetupHistoryService,
    @Autowired private val setupSessionService: SetupSessionService,
    @Autowired private val setupMessageService: SetupMessageService,
    @Autowired private val fakeBookSourceGateway: FakeBookSourceGateway,
    @Autowired private val editionCache: EditionCache,
    @Autowired private val jdbc: JdbcClient,
) {
    @BeforeEach
    fun reset() {
        fakeBookSourceGateway.reset()
        editionCache.clear()
    }

    // Rolled back with the test; no one signs in, so the hash is never read.
    private fun accountId(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "h-" + id.toString().take(12))
            .update()
        return id
    }

    private fun said(text: String) = SetupMessageRequest(text = text)

    private fun revision(
        account: UUID,
        sessionId: UUID,
    ) = setupSessionService.get(account, sessionId).revision

    private fun pick(
        account: UUID,
        sessionId: UUID,
    ) = setupSessionService.pick(account, sessionId, SetupPickRequest("101", revision = revision(account, sessionId)))

    private fun picked(account: UUID): SetupSessionResponse {
        fakeBookSourceGateway.holds("101")
        val session = setupMessageService.start(account, said("오브젝트"))
        return pick(account, session.id)
    }

    private fun started(
        account: UUID,
        sessionId: UUID,
    ) = SetupConfirmRequest(revision = revision(account, sessionId), level = "work", voice = "explain")

    /**
     * A session as an earlier conversation left it, opened at [at], its messages said a minute apart
     * after that; each is a role, a kind and its payload.
     */
    private fun conversation(
        account: UUID,
        at: Instant,
        vararg messages: Triple<String, String, String>,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into setup_session (id, version, account_id, state, revision, created_at, updated_at)
                values (:id, 0, :account, 'searching', 0, :at, :at)
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("at", Timestamp.from(at))
            .update()
        messages.forEachIndexed { i, (role, kind, payload) ->
            jdbc
                .sql(
                    """
                    insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
                    values (:id, :session, :account, :seq, :role, :kind, cast(:payload as jsonb), :at)
                    """.trimIndent(),
                ).param("id", UUID.randomUUID())
                .param("session", id)
                .param("account", account)
                .param("seq", i + 1)
                .param("role", role)
                .param("kind", kind)
                .param("payload", payload)
                .param("at", Timestamp.from(at.plus(Duration.ofMinutes(i + 1L))))
                .update()
        }
        return id
    }

    private fun asked(text: String) = Triple("reader", "text", """{"text": "$text"}""")

    private fun answered(text: String) = Triple("gloss", "said", """{"text": "$text"}""")

    private fun listed(
        account: UUID,
        bookId: UUID? = null,
    ): List<SetupSessionSummaryResponse> = setupHistoryService.list(account, bookId)

    @Test
    fun `a reader's list holds the sessions they wrote in, latest activity first`() {
        val account = accountId()
        val answeredLast = conversation(account, EARLIER, asked("오브젝트"), answered("찾았어요"), answered("더 있어요"))
        val openedLater = conversation(account, EARLIER.plusSeconds(60), asked("리팩터링"))
        conversation(account, EARLIER.plusSeconds(120))
        conversation(account, EARLIER.plusSeconds(180), answered("무엇을 읽을까요"))

        val list = listed(account)

        assertThat(list.map { it.id }).containsExactly(answeredLast, openedLater)
        assertThat(list[0].createdAt).isEqualTo(EARLIER)
        assertThat(list[0].lastActivityAt).isEqualTo(EARLIER.plus(Duration.ofMinutes(3)))
        assertThat(list[1].lastActivityAt).isEqualTo(EARLIER.plus(Duration.ofMinutes(2)))
        assertThat(list.map { it.state }).containsOnly("searching")
    }

    @Test
    fun `a reader lists only their own sessions`() {
        val account = accountId()
        val own = conversation(account, EARLIER, asked("오브젝트"))
        conversation(accountId(), EARLIER.plusSeconds(60), asked("리팩터링"))

        assertThat(listed(account).map { it.id }).containsExactly(own)
    }

    @Test
    fun `a session is titled by its book, else by the reader's first message cut to a line`() {
        val account = accountId()
        val long = "객체지향".repeat(30)
        val asking = conversation(account, EARLIER, asked(long), answered("찾았어요"), asked("다른 책"))
        fakeBookSourceGateway.holds("101")
        val dropped = setupMessageService.start(account, said("설계 입문서"))
        pick(account, dropped.id)
        setupMessageService.say(
            account,
            dropped.id,
            SetupMessageRequest("다른 판본", revision = revision(account, dropped.id)),
        )
        val chosen = setupMessageService.start(account, said("객체지향 입문서"))
        pick(account, chosen.id)

        val titles = listed(account).associate { it.id to it.title }

        assertThat(titles[asking]).isEqualTo(long.take(80) + "…")
        assertThat(titles[dropped.id]).isEqualTo("설계 입문서")
        assertThat(titles[chosen.id]).isEqualTo("오브젝트")
    }

    @Test
    fun `a book finds the session that made it, and only that one`() {
        val account = accountId()
        conversation(account, EARLIER, asked("리팩터링"))
        val chosen = picked(account)
        val started = setupSessionService.confirm(account, chosen.id, started(account, chosen.id))
        val bookId = checkNotNull(started.bookId)

        val found = listed(account, bookId)

        assertThat(found.map { it.id to it.state }).containsExactly(chosen.id to "confirmed")
        assertThat(found.single().bookId).isEqualTo(bookId)
        assertThat(found.single().title).isEqualTo("오브젝트")
        assertThat(listed(accountId(), bookId)).isEmpty()
        assertThat(listed(account)).hasSize(2)
    }

    private companion object {
        val EARLIER: Instant = Instant.parse("2026-10-01T09:00:00Z")
    }
}
