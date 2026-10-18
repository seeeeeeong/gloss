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
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.request.SetupPickRequest
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
class SetupEditionsServiceTest(
    @Autowired private val setupSessionService: SetupSessionService,
    @Autowired private val setupMessageService: SetupMessageService,
    @Autowired private val setupEditionsService: SetupEditionsService,
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

    @Test
    fun `a recommendation's editions are asked for again, in its order, each search once`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "오브젝트 원서")
        val session = setupMessageService.start(account, said("오브젝트"))
        // As the model's recommendation of both is kept, the original first.
        val seq = session.messages.last().seq + 1
        jdbc
            .sql(
                """
                insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
                values (:id, :session, :account, :seq, 'gloss', 'recommend', cast(:payload as jsonb), now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("session", session.id)
            .param("account", account)
            .param("seq", seq)
            .param(
                "payload",
                """{"callId": "r1", "intro": "두 권", "books": [{"ref": "102", "reason": "원서", "sources": []},""" +
                    """ {"ref": "101", "reason": "번역서", "sources": []}]}""",
            ).update()
        editionCache.clear()
        fakeBookSourceGateway.asked.clear()

        val shown = setupEditionsService.books(account, session.id, seq)

        assertThat(shown.map { it.ref to it.title }).containsExactly("102" to "오브젝트 원서", "101" to "오브젝트")
        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트")
    }

    @Test
    fun `a list is shown by asking again, in the order it was shown, never from what is kept`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", contents = null, title = "오브젝트 원서")
        val session = setupMessageService.start(account, said("오브젝트"))

        val shown = setupEditionsService.books(account, session.id, BOOKS_SEQ)
        editionCache.clear()
        fakeBookSourceGateway.reset()
        fakeBookSourceGateway.holds("101")
        val later = setupEditionsService.books(account, session.id, BOOKS_SEQ)

        assertThat(shown.map { it.ref to it.title }).containsExactly("101" to "오브젝트", "102" to "오브젝트 원서")
        assertThat(shown.first().url).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(shown.first().cover).isEqualTo("https://image.yes24.com/goods/101/L")
        assertThat(shown.first().publishedOn).isEqualTo(LocalDate.of(2019, 6, 17))
        assertThat(later.map { it.ref }).containsExactly("101")
        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트")
        assertFails(SetupError.MESSAGE_NOT_FOUND) { setupEditionsService.books(account, session.id, 1) }
        assertFails(SetupError.SESSION_NOT_FOUND) { setupEditionsService.books(accountId(), session.id, BOOKS_SEQ) }
    }

    // Committed, so nothing but the service itself opens a transaction around the source.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a session is read in a transaction that ends before the source is asked to describe its pick`() {
        val account = accountId()
        val chosen = picked(account)
        editionCache.clear()
        fakeBookSourceGateway.inTransaction.clear()

        val again = setupSessionService.get(account, chosen.id)

        assertThat(again.held?.book?.title).isEqualTo("오브젝트")
        assertThat(fakeBookSourceGateway.inTransaction).containsExactly(false)
    }

    @Test
    fun `the source's name and page show with the held edition even when it cannot be asked`() {
        val account = accountId()
        val chosen = picked(account)
        editionCache.clear()
        fakeBookSourceGateway.failing = BookSourceFailed.Kind.FAILED

        val again = setupSessionService.get(account, chosen.id)

        assertThat(again.held?.book).isNull()
        assertThat(again.held?.source).isEqualTo("yes24")
        assertThat(again.held?.sourceUrl).isEqualTo("https://www.yes24.com/product/goods/101")
        assertThat(again.held?.title).isEqualTo("오브젝트")
    }

    @Test
    fun `a list shows each edition as it was offered, and a pick whose contents are gone says so`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        val session = setupMessageService.start(account, said("오브젝트"))
        editionCache.clear()
        fakeBookSourceGateway.reset()
        fakeBookSourceGateway.holds("101", contents = null)

        val shown = setupEditionsService.books(account, session.id, BOOKS_SEQ)

        assertThat(shown.single().hasContents).isTrue()
        assertFails(SetupError.CONTENTS_GONE) {
            pickRef(account, session.id, "101")
        }
    }

    private companion object {
        // The reader's message, the model's call, then what the search found.
        const val BOOKS_SEQ = 3
    }
}
