package gloss.domain.setup.service

import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.event.TocConfirmed
import gloss.domain.book.service.BookError
import gloss.domain.book.service.BookService
import gloss.domain.book.service.EditionCache
import gloss.domain.setup.TitleSearchModel
import gloss.domain.setup.domain.BooksFound
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.ReaderConfirmed
import gloss.domain.setup.domain.ReaderPicked
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderTyped
import gloss.domain.setup.domain.SearchFailed
import gloss.domain.setup.domain.SetupDone
import gloss.domain.setup.dto.request.SetupConfirmRequest
import gloss.domain.setup.dto.request.SetupContentsRequest
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.request.SetupPickRequest
import gloss.domain.setup.dto.request.SetupTypedRequest
import gloss.domain.setup.dto.response.SetupSessionResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeBookSourceGateway.Config::class, TitleSearchModel.Config::class)
@Transactional
@RecordApplicationEvents
class SetupSessionServiceIntegrationTest(
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
            .param("loginId", "s-" + id.toString().take(12))
            .update()
        return id
    }

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private fun said(text: String) = SetupMessageRequest(text = text)

    private fun picked(
        account: UUID,
        ref: String = "101",
    ): SetupSessionResponse {
        fakeBookSourceGateway.holds(ref)
        val session = setupMessageService.start(account, said("오브젝트"))
        return pickRef(account, session.id, ref)
    }

    // Each request names the session's revision as the reader last saw it.
    private fun rev(
        account: UUID,
        sessionId: UUID,
    ) = setupSessionService.get(account, sessionId).revision

    private fun seen(
        account: UUID,
        sessionId: UUID,
    ) = SetupConfirmRequest(revision = rev(account, sessionId))

    private fun say(
        account: UUID,
        sessionId: UUID,
        text: String,
    ) = setupMessageService.say(account, sessionId, SetupMessageRequest(text, revision = rev(account, sessionId)))

    private fun pickRef(
        account: UUID,
        sessionId: UUID,
        ref: String,
    ) = setupSessionService.pick(account, sessionId, SetupPickRequest(ref, revision = rev(account, sessionId)))

    private fun books(account: UUID): Int =
        jdbc
            .sql("select count(*) from book where account_id = :account")
            .param("account", account)
            .query(Int::class.java)
            .single()

    // Committed, as two requests at once are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two starts at once make one book, and both answer with it`() {
        val account = accountId()
        val chosen = picked(account)
        val request = seen(account, chosen.id)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val outcomes =
            List(2) {
                pool.submit<String> {
                    start.await(5, TimeUnit.SECONDS)
                    runCatching { setupSessionService.confirm(account, chosen.id, request) }
                        .fold({ "started" }, { (it as GlossException).error.code })
                }
            }
        start.countDown()

        // The second waited for the first's lock and found its book: both answer with it.
        assertThat(outcomes.map { it.get(10, TimeUnit.SECONDS) }).containsExactly("started", "started")
        assertThat(books(account)).isEqualTo(1)
        pool.shutdown()
    }

    // Committed, as two tabs' requests are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a message, a pick or typed contents while a message is answered are refused, so answers keep their order`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        val session = setupMessageService.start(account, said("오브젝트"))
        val gate = CountDownLatch(1)
        fakeBookSourceGateway.holdCalls(gate)
        val pool = Executors.newSingleThreadExecutor()
        val first = pool.submit<SetupSessionResponse> { say(account, session.id, "첫째") }
        fakeBookSourceGateway.calling.await(5, TimeUnit.SECONDS)

        assertFails(SetupError.STILL_SEARCHING) { say(account, session.id, "둘째") }
        assertFails(SetupError.STILL_SEARCHING) {
            pickRef(account, session.id, "101")
        }
        assertFails(SetupError.STILL_SEARCHING) {
            setupSessionService.type(
                account,
                session.id,
                SetupTypedRequest(title = "나의 노트", toc = "1장 시작", revision = rev(account, session.id)),
            )
        }
        assertThat(setupSessionService.get(account, session.id).searching).isTrue()
        gate.countDown()
        val answered = first.get(10, TimeUnit.SECONDS)

        assertThat(answered.searching).isFalse()
        assertThat(answered.messages.map { it.kind }.filter { it == "text" || it == "books" })
            .containsExactly("text", "books", "text", "books")
        assertThat((answered.messages.last { it.kind == "books" }.payload as BooksFound).query).isEqualTo("첫째")
        pool.shutdown()
    }

    // Committed, as two tabs' requests are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `contents edited in another tab while a start reads the source are not the ones started with`() {
        val account = accountId()
        val chosen = picked(account)
        val before = seen(account, chosen.id)
        val gate = CountDownLatch(1)
        fakeBookSourceGateway.holdCalls(gate)
        val pool = Executors.newSingleThreadExecutor()
        val start =
            pool.submit<String> {
                runCatching { setupSessionService.confirm(account, chosen.id, before) }
                    .fold({ "started" }, { (it as GlossException).error.code })
            }
        fakeBookSourceGateway.calling.await(5, TimeUnit.SECONDS)

        setupSessionService.edit(
            account,
            chosen.id,
            SetupContentsRequest("1장 객체", revision = rev(account, chosen.id)),
        )
        gate.countDown()

        assertThat(start.get(10, TimeUnit.SECONDS)).isEqualTo(SetupError.SESSION_CHANGED.code)
        assertThat(books(account)).isZero()
        pool.shutdown()
    }

    // Committed, as two tabs' requests are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a pick that waited on the source does not undo a change another tab made meanwhile`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", isbn13 = "9788998139766", contents = "1장 다른 판본")
        val chosen = picked(account)
        val gate = CountDownLatch(1)
        fakeBookSourceGateway.holdCalls(gate)
        val pool = Executors.newSingleThreadExecutor()
        val late =
            pool.submit<String> {
                runCatching {
                    setupSessionService.pick(
                        account,
                        chosen.id,
                        SetupPickRequest("102", revision = chosen.revision),
                    )
                }.fold({ "picked" }, { (it as GlossException).error.code })
            }
        fakeBookSourceGateway.calling.await(5, TimeUnit.SECONDS)

        setupSessionService.edit(account, chosen.id, SetupContentsRequest("1장 다른 탭", revision = chosen.revision))
        gate.countDown()

        assertThat(late.get(10, TimeUnit.SECONDS)).isEqualTo(SetupError.SESSION_CHANGED.code)
        val held = setupSessionService.get(account, chosen.id).held
        assertThat(held?.ref).isEqualTo("101")
        assertThat(held?.raw).isEqualTo("1장 다른 탭")
        pool.shutdown()
    }
}
