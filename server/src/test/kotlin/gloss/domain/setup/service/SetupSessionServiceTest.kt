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
import gloss.domain.setup.domain.GlossSaid
import gloss.domain.setup.domain.ReaderConfirmed
import gloss.domain.setup.domain.ReaderPicked
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderTyped
import gloss.domain.setup.domain.SetupDone
import gloss.domain.setup.domain.ToolRefused
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
class SetupSessionServiceTest(
    @Autowired private val setupSessionService: SetupSessionService,
    @Autowired private val setupMessageService: SetupMessageService,
    @Autowired private val bookService: BookService,
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

    private val source = "1장 객체, 설계\n1.1 티켓 판매\n2장 객체지향"

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
    ) = SetupConfirmRequest(revision = rev(account, sessionId), level = "work", voice = "explain")

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

    // What the reader sees of a session; the model's own calls are SetupTurnServiceTest's.
    private fun kinds(session: SetupSessionResponse) = session.messages.map { it.kind }.filterNot { it in MODEL }

    private fun payloadOf(
        session: SetupSessionResponse,
        kind: String,
    ) = session.messages.last { it.kind == kind }.payload

    private companion object {
        val MODEL = setOf("tool_calls", "books_shown")
    }

    @Test
    fun `a session starts with the reader's message, answered with the editions a search found`() {
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", contents = null, title = "오브젝트 원서")

        val session = setupMessageService.start(accountId(), said("  오브젝트\n코드로 이해하는 객체지향 "))

        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트 코드로 이해하는 객체지향")
        assertThat(session.state).isEqualTo("searching")
        assertThat(session.bookId).isNull()
        assertThat(session.messages.map { it.seq to (it.role to it.kind) }).containsExactly(
            1 to ("reader" to "text"),
            2 to ("gloss" to "tool_calls"),
            3 to ("gloss" to "books"),
            4 to ("gloss" to "tool_calls"),
            5 to ("gloss" to "books_shown"),
        )
        assertThat(session.messages[0].payload).isEqualTo(ReaderSaid("오브젝트 코드로 이해하는 객체지향"))
        val found = payloadOf(session, "books") as BooksFound
        assertThat(found.query).isEqualTo("오브젝트 코드로 이해하는 객체지향")
        assertThat(found.books.map { it.ref to it.hasContents }).containsExactly("101" to true, "102" to false)
        val kept =
            jdbc
                .sql("select payload::text from setup_message where session_id = :session and kind = 'books'")
                .param("session", session.id)
                .query(String::class.java)
                .single()
        assertThat(kept).doesNotContain("위키북스", "yes24.com", "조영호")
    }

    @Test
    fun `a search that fails is told to the model, which answers, and the reader may search again`() {
        val account = accountId()
        fakeBookSourceGateway.failing = BookSourceFailed.Kind.UNAVAILABLE

        val session = setupMessageService.start(account, said("오브젝트"))
        fakeBookSourceGateway.failing = null
        val again = say(account, session.id, "오브젝트 조영호")

        assertThat((payloadOf(session, "tool_refused") as ToolRefused).problem)
            .contains(BookError.SOURCE_UNAVAILABLE.code)
        assertThat(payloadOf(session, "said")).isEqualTo(GlossSaid(TitleSearchModel.NOTHING_FOUND))
        assertThat(kinds(again)).containsExactly("text", "tool_refused", "said", "text", "books", "said")
        assertThat((payloadOf(again, "books") as BooksFound).books).isEmpty()
        assertThat(again.searching).isFalse()
    }

    @Test
    fun `a message too long is refused before anything is kept or searched`() {
        val account = accountId()

        assertFails(SetupError.INVALID_TEXT) { setupMessageService.start(account, said("가".repeat(501))) }
        assertThat(fakeBookSourceGateway.asked).isEmpty()
        assertThat(
            jdbc
                .sql("select count(*) from setup_session where account_id = :account")
                .param("account", account)
                .query(Int::class.java)
                .single(),
        ).isZero()
    }

    @Test
    fun `a pick holds the edition's contents in the session, and no book is made yet`() {
        val account = accountId()

        val session = picked(account)

        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트", "101")
        assertThat(session.state).isEqualTo("book_chosen")
        assertThat(session.bookId).isNull()
        assertThat(kinds(session)).containsExactly("text", "books", "pick", "contents")
        assertThat(payloadOf(session, "pick")).isEqualTo(ReaderPicked(ref = "101", title = "오브젝트"))
        val brought = payloadOf(session, "contents") as ContentsBrought
        assertThat(brought.ref).isEqualTo("101")
        assertThat(brought.title).isEqualTo("오브젝트")
        assertThat(brought.items).isEqualTo(3)
        assertThat(brought.typed).isFalse()
        val held = checkNotNull(session.held)
        assertThat(held.ref).isEqualTo("101")
        assertThat(held.raw).isEqualTo(source)
        assertThat(held.book?.title).isEqualTo("오브젝트")
        assertThat(held.items.map { Triple(it.ord, it.parent, it.title) })
            .containsExactly(Triple(0, null, "객체, 설계"), Triple(1, 0, "티켓 판매"), Triple(2, null, "객체지향"))
        assertThat(books(account)).isZero()
    }

    @Test
    fun `only an edition with contents that this session found is picked`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", contents = null)
        val session = setupMessageService.start(account, said("오브젝트"))
        fakeBookSourceGateway.holds("103")
        // Found, but by another session of the same reader's.
        setupMessageService.start(account, said("오브젝트 원서"))

        assertFails(SetupError.BOOK_NOT_OFFERED) {
            pickRef(account, session.id, "103")
        }
        assertFails(SetupError.BOOK_NOT_OFFERED) {
            pickRef(account, session.id, "102")
        }
        assertThat(fakeBookSourceGateway.asked).containsExactly("오브젝트", "오브젝트 원서")
    }

    @Test
    fun `only editions the reader was shown are picked, never a search the model kept to itself`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        val session = setupMessageService.start(account, said("오브젝트"))
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", title = "숨은 판본")
        fakeBookSourceGateway.holds(ref = "103", isbn13 = "9791158391409", title = "추천 판본")
        fakeBookSourceGateway.holds(ref = "104", isbn13 = "9788966261208", contents = null, title = "목차 없는 숨은 판본")
        val hidden =
            """{"query": "숨은 검색", "callId": "hidden", "books": [{"ref": "102", "hasContents": true},""" +
                """ {"ref": "103", "hasContents": true}, {"ref": "104", "hasContents": false}]}"""
        val recommended =
            """{"callId": "rec", "intro": "골랐어요.", "books": [{"ref": "101", "reason": "번역서", "sources": []},""" +
                """ {"ref": "103", "reason": "추천", "sources": []}]}"""
        keep(account = account, sessionId = session.id, kind = "books", payload = hidden)
        keep(account = account, sessionId = session.id, kind = "recommend", payload = recommended)

        assertFails(SetupError.BOOK_NOT_OFFERED) { pickRef(account, session.id, "102") }
        assertFails(SetupError.BOOK_NOT_OFFERED) {
            setupSessionService.type(
                account,
                session.id,
                SetupTypedRequest(ref = "104", toc = "1장 객체", revision = rev(account, session.id)),
            )
        }
        assertThat(pickRef(account, session.id, "103").held?.ref).isEqualTo("103")
    }

    private fun keep(
        account: UUID,
        sessionId: UUID,
        kind: String,
        payload: String,
    ) = jdbc
        .sql(
            """
            insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
            select :id, :session, :account, max(seq) + 1, 'gloss', :kind, cast(:payload as jsonb), now()
            from setup_message where session_id = :session
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("session", sessionId)
        .param("account", account)
        .param("kind", kind)
        .param("payload", payload)
        .update()

    @Test
    fun `a message after a pick searches again, and an edition from an earlier list replaces the pick`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds("102", isbn13 = "9788998139766", contents = "1장 다른 판본")
        val chosen =
            pickRef(account, setupMessageService.start(account, said("오브젝트")).id, "101")

        val again = say(account, chosen.id, "오브젝트 2판")
        val replaced = pickRef(account, chosen.id, "102")

        assertThat(again.state).isEqualTo("searching")
        assertThat(again.held).isNull()
        assertThat(kinds(again)).containsExactly("text", "books", "pick", "contents", "text", "books")
        assertThat(replaced.state).isEqualTo("book_chosen")
        assertThat(replaced.held?.ref).isEqualTo("102")
        assertThat(replaced.held?.raw).isEqualTo("1장 다른 판본")
        val picked = pickRef(account, chosen.id, "101")
        assertThat(picked.held?.ref).isEqualTo("101")
        assertThat(books(account)).isZero()
    }

    @Test
    fun `the held contents are edited as a book's are checked, and still make no book`() {
        val account = accountId()
        val chosen = picked(account)

        val edited =
            setupSessionService.edit(
                account,
                chosen.id,
                SetupContentsRequest("1장 객체\n  1.1 티켓\n2장 설계", revision = rev(account, chosen.id)),
            )

        assertThat(edited.held?.raw).isEqualTo("1장 객체\n  1.1 티켓\n2장 설계")
        assertThat(edited.held?.items?.map { it.title }).containsExactly("객체", "티켓", "설계")
        assertFails(BookError.TOC_TOO_DEEP) {
            setupSessionService.edit(
                account,
                chosen.id,
                SetupContentsRequest("1.2.3.4.5.6.7 너무 깊은 절", revision = rev(account, chosen.id)),
            )
        }
        assertThat(setupSessionService.get(account, chosen.id).held?.raw).isEqualTo("1장 객체\n  1.1 티켓\n2장 설계")
        assertThat(books(account)).isZero()
    }

    @Test
    fun `starting makes the book and confirms it through the book, so the plan is asked for as before`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()
        val chosen = picked(account)

        val session = setupSessionService.confirm(account, chosen.id, seen(account, chosen.id))

        val bookId = checkNotNull(session.bookId)
        val book = bookService.get(account, bookId)
        assertThat(session.state).isEqualTo("confirmed")
        assertThat(session.held).isNull()
        assertThat(kinds(session)).containsExactly("text", "books", "pick", "contents", "confirm", "done")
        assertThat(payloadOf(session, "confirm"))
            .isEqualTo(ReaderConfirmed(tocId = book.toc.id, level = "work", voice = "explain"))
        assertThat(payloadOf(session, "done")).isEqualTo(SetupDone(bookId = bookId, items = book.toc.items.size))
        assertThat(books(account)).isEqualTo(1)
        assertThat(book.source).isEqualTo("yes24")
        assertThat(book.level to book.voice).isEqualTo("work" to "explain")
        assertThat(book.toc.versionNo).isEqualTo(1)
        assertThat(book.toc.origin).isEqualTo("api")
        assertThat(book.currentTocId).isEqualTo(book.toc.id)
        assertThat(events.stream(TocConfirmed::class.java).map { it.bookId to it.tocId })
            .containsExactly(bookId to book.toc.id)
        assertFails(SetupError.ALREADY_CONFIRMED) {
            setupSessionService.confirm(
                account,
                chosen.id,
                SetupConfirmRequest(revision = 0, level = "work", voice = "explain"),
            )
        }
        assertFails(SetupError.ALREADY_CONFIRMED) { say(account, chosen.id, "객체지향") }
    }

    @Test
    fun `a start without a level and voice offered makes nothing`() {
        val account = accountId()
        val chosen = picked(account)
        val revision = seen(account, chosen.id).revision

        assertFails(SetupError.INVALID_STYLE) {
            val request = SetupConfirmRequest(revision = revision, level = "expert", voice = "explain")
            setupSessionService.confirm(account, chosen.id, request)
        }
        assertFails(SetupError.INVALID_STYLE) {
            val request = SetupConfirmRequest(revision = revision, level = "course", voice = "")
            setupSessionService.confirm(account, chosen.id, request)
        }
        assertThat(setupSessionService.get(account, chosen.id).state).isEqualTo("book_chosen")
        assertThat(books(account)).isZero()
    }

    @Test
    fun `a start repeated with what the reader saw, as after a lost answer, answers with the same book`() {
        val account = accountId()
        val chosen = picked(account)
        val request = seen(account, chosen.id)

        val first = setupSessionService.confirm(account, chosen.id, request)
        val again = setupSessionService.confirm(account, chosen.id, request)

        assertThat(again.state).isEqualTo("confirmed")
        assertThat(again.bookId).isEqualTo(first.bookId)
        assertThat(again.messages).hasSameSizeAs(first.messages)
        assertThat(books(account)).isEqualTo(1)
        assertFails(SetupError.ALREADY_CONFIRMED) {
            setupSessionService.confirm(
                account,
                chosen.id,
                SetupConfirmRequest(revision = request.revision + 1, level = "work", voice = "explain"),
            )
        }
    }

    @Test
    fun `edited contents are the reader's own version after the source's, and that one is confirmed`() {
        val account = accountId()
        val chosen = picked(account)
        setupSessionService.edit(
            account,
            chosen.id,
            SetupContentsRequest("1장 객체\n2장 설계", revision = rev(account, chosen.id)),
        )

        val session = setupSessionService.confirm(account, chosen.id, seen(account, chosen.id))

        val book = bookService.get(account, checkNotNull(session.bookId))
        assertThat(book.toc.versionNo).isEqualTo(2)
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.toc.raw).isEqualTo("1장 객체\n2장 설계")
        assertThat(book.currentTocId).isEqualTo(book.toc.id)
    }

    @Test
    fun `starting with contents other than the ones held makes nothing`() {
        val account = accountId()
        val chosen = picked(account)
        val before = seen(account, chosen.id)
        setupSessionService.edit(
            account,
            chosen.id,
            SetupContentsRequest("1장 객체", revision = rev(account, chosen.id)),
        )

        assertFails(SetupError.SESSION_CHANGED) { setupSessionService.confirm(account, chosen.id, before) }
        assertThat(books(account)).isZero()
    }

    @Test
    fun `a book already on the shelf is not made again, and the session keeps its pick`() {
        val account = accountId()
        picked(account).let { setupSessionService.confirm(account, it.id, seen(account, it.id)) }
        val chosen = picked(account)

        assertFails(BookError.ISBN_TAKEN) {
            setupSessionService.confirm(account, chosen.id, seen(account, chosen.id))
        }
        assertThat(setupSessionService.get(account, chosen.id).state).isEqualTo("book_chosen")
        assertThat(books(account)).isEqualTo(1)
    }

    @Test
    fun `an edit made on contents another tab has changed since is refused, and theirs is kept`() {
        val account = accountId()
        val chosen = picked(account)
        val seen = chosen.revision
        setupSessionService.edit(account, chosen.id, SetupContentsRequest("1장 탭 A", revision = seen))

        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.edit(account, chosen.id, SetupContentsRequest("1장 탭 B", revision = seen))
        }
        assertThat(setupSessionService.get(account, chosen.id).held?.raw).isEqualTo("1장 탭 A")
    }

    @Test
    fun `nothing is edited or started before a book is picked`() {
        val account = accountId()
        val session = setupMessageService.start(account, said("오브젝트"))

        assertFails(SetupError.NO_BOOK_YET) {
            setupSessionService.confirm(
                account,
                session.id,
                SetupConfirmRequest(revision = 1, level = "work", voice = "explain"),
            )
        }
        assertFails(SetupError.NO_BOOK_YET) {
            setupSessionService.edit(account, session.id, SetupContentsRequest("1장 객체", revision = 1))
        }
    }

    @Test
    fun `contents typed for an edition the source has none for are held as a pick's, and make the book on start`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", contents = null, title = "오브젝트 원서")
        val session = setupMessageService.start(account, said("오브젝트"))

        val held =
            setupSessionService.type(
                account,
                session.id,
                SetupTypedRequest(ref = "102", toc = "1장 객체\n2장 설계", revision = rev(account, session.id)),
            )

        assertThat(held.state).isEqualTo("book_chosen")
        assertThat(kinds(held)).containsExactly("text", "books", "typed", "contents")
        assertThat(payloadOf(held, "typed")).isEqualTo(ReaderTyped("오브젝트 원서"))
        val brought = payloadOf(held, "contents") as ContentsBrought
        assertThat(
            listOf(brought.ref, brought.title, brought.items, brought.typed),
        ).containsExactly("102", "오브젝트 원서", 2, true)
        assertThat(held.held?.ref).isEqualTo("102")
        assertThat(held.held?.book?.ref).isEqualTo("102")
        assertThat(held.held?.raw).isEqualTo("1장 객체\n2장 설계")
        assertThat(books(account)).isZero()
        assertFails(SetupError.BOOK_NOT_OFFERED) {
            setupSessionService.type(
                account,
                session.id,
                SetupTypedRequest(ref = "101", toc = "1장 객체", revision = rev(account, session.id)),
            )
        }

        val started = setupSessionService.confirm(account, session.id, seen(account, session.id))

        val book = bookService.get(account, checkNotNull(started.bookId))
        assertThat(book.source).isEqualTo("yes24")
        assertThat(book.toc.versionNo).isEqualTo(1)
        assertThat(book.toc.origin).isEqualTo("manual")
        assertThat(book.currentTocId).isEqualTo(book.toc.id)
        assertThat(books(account)).isEqualTo(1)
    }

    @Test
    fun `a book typed in whole starts a session of its own, and is made only on start`() {
        val account = accountId()

        val held =
            setupSessionService.type(
                account,
                null,
                SetupTypedRequest(title = " 나의 노트 ", authors = listOf("나", " "), toc = "1장 시작\n1.1 준비"),
            )

        assertThat(held.state).isEqualTo("book_chosen")
        assertThat(held.messages.map { it.kind }).containsExactly("typed", "contents")
        assertThat(held.held?.ref).isNull()
        assertThat(held.held?.title).isEqualTo("나의 노트")
        assertThat(held.held?.authors).containsExactly("나")
        assertThat(held.held?.book).isNull()
        assertThat(books(account)).isZero()
        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.confirm(
                account,
                held.id,
                SetupConfirmRequest(revision = 0, level = "work", voice = "explain"),
            )
        }

        val started = setupSessionService.confirm(account, held.id, seen(account, held.id))

        val book = bookService.get(account, checkNotNull(started.bookId))
        assertThat(book.title).isEqualTo("나의 노트")
        assertThat(book.source).isEqualTo("manual")
        assertThat(book.currentTocId).isEqualTo(book.toc.id)
        assertThat(books(account)).isEqualTo(1)
        assertThat(fakeBookSourceGateway.asked).isEmpty()
    }

    @Test
    fun `a typed book is checked as a book is before anything is kept`() {
        val account = accountId()

        assertFails(BookError.INVALID_TITLE) {
            setupSessionService.type(account, null, SetupTypedRequest(title = " ", toc = "1장 시작"))
        }
        assertFails(BookError.TOC_TOO_DEEP) {
            setupSessionService.type(account, null, SetupTypedRequest(title = "책", toc = "1.2.3.4.5.6.7 너무 깊은 절"))
        }
        assertThat(
            jdbc
                .sql("select count(*) from setup_session where account_id = :account")
                .param("account", account)
                .query(Int::class.java)
                .single(),
        ).isZero()
    }

    @Test
    fun `contents for an edition are typed in a session, never without one`() {
        assertFails(SetupError.BOOK_NOT_OFFERED) {
            setupSessionService.type(accountId(), null, SetupTypedRequest(ref = "102", toc = "1장 객체"))
        }
        assertThat(fakeBookSourceGateway.asked).isEmpty()
    }

    @Test
    fun `a message left unanswered past its time no longer holds the next one back`() {
        val account = accountId()
        val session = setupMessageService.start(account, said("오브젝트"))
        jdbc
            .sql("update setup_session set searching_since = now() - interval '5 minutes' where id = :id")
            .param("id", session.id)
            .update()

        val again = say(account, session.id, "오브젝트 원서")

        assertThat(again.searching).isFalse()
        assertThat(kinds(again)).containsExactly("text", "books", "said", "text", "books", "said")
    }

    @Test
    fun `another reader's session is not found`() {
        val session = setupMessageService.start(accountId(), said("오브젝트"))
        val stranger = accountId()

        assertFails(SetupError.SESSION_NOT_FOUND) { setupSessionService.get(stranger, session.id) }
        assertFails(SetupError.SESSION_NOT_FOUND) { say(stranger, session.id, "오브젝트") }
        assertFails(SetupError.SESSION_NOT_FOUND) {
            pickRef(stranger, session.id, "101")
        }
    }

    @Test
    fun `every change names the revision the reader saw, and one made on an older revision changes nothing`() {
        val account = accountId()
        fakeBookSourceGateway.holds("101")
        fakeBookSourceGateway.holds(ref = "102", isbn13 = "9788998139766", contents = null, title = "오브젝트 원서")
        val searching = setupMessageService.start(account, said("오브젝트"))
        val stale = searching.revision - 1
        val chosen = pickRef(account, searching.id, "101")
        val old = chosen.revision - 1

        assertFails(SetupError.SESSION_CHANGED) {
            setupMessageService.say(account, chosen.id, SetupMessageRequest("객체지향", revision = old))
        }
        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.pick(account, chosen.id, SetupPickRequest("101", revision = old))
        }
        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.type(account, chosen.id, SetupTypedRequest(ref = "102", toc = "1장", revision = old))
        }
        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.edit(account, chosen.id, SetupContentsRequest("1장 객체", revision = old))
        }
        assertFails(SetupError.SESSION_CHANGED) {
            setupSessionService.confirm(
                account,
                chosen.id,
                SetupConfirmRequest(revision = old, level = "work", voice = "explain"),
            )
        }
        val after = setupSessionService.get(account, chosen.id)
        assertThat(after.revision).isEqualTo(chosen.revision)
        assertThat(after.messages).hasSameSizeAs(chosen.messages)
        assertThat(after.held?.raw).isEqualTo(chosen.held?.raw)
        assertThat(stale).isLessThan(old)
        assertThat(books(account)).isZero()
    }

    @Test
    fun `the held title is read with the held contents from the session itself, never from a later message`() {
        val account = accountId()
        val chosen = picked(account)
        jdbc
            .sql(
                """
                insert into setup_message (id, session_id, account_id, seq, role, kind, payload, created_at)
                values (:id, :session, :account, 99, 'gloss', 'contents',
                        '{"ref": "102", "title": "다른 판본", "items": 1, "typed": false}', now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("session", chosen.id)
            .param("account", account)
            .update()

        val again = setupSessionService.get(account, chosen.id)

        assertThat(again.held?.ref).isEqualTo("101")
        assertThat(again.held?.title).isEqualTo("오브젝트")
    }
}
