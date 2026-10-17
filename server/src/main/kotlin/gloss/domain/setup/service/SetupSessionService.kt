package gloss.domain.setup.service

import gloss.common.error.CommonError
import gloss.common.error.GlossException
import gloss.domain.book.domain.style.WritingStyle
import gloss.domain.book.service.BookService
import gloss.domain.book.service.BookSourceService
import gloss.domain.book.service.ContentsReader
import gloss.domain.book.service.OutlineItem
import gloss.domain.book.service.TypedBook
import gloss.domain.setup.domain.ContentsBrought
import gloss.domain.setup.domain.FoundEdition
import gloss.domain.setup.domain.ReaderConfirmed
import gloss.domain.setup.domain.ReaderPicked
import gloss.domain.setup.domain.ReaderTyped
import gloss.domain.setup.domain.SetupDone
import gloss.domain.setup.domain.SetupMessage
import gloss.domain.setup.domain.SetupPolicy
import gloss.domain.setup.domain.SetupSession
import gloss.domain.setup.domain.ShownEditions
import gloss.domain.setup.domain.repository.SetupSessionRepository
import gloss.domain.setup.dto.request.SetupConfirmRequest
import gloss.domain.setup.dto.request.SetupContentsRequest
import gloss.domain.setup.dto.request.SetupPickRequest
import gloss.domain.setup.dto.request.SetupTypedRequest
import gloss.domain.setup.dto.response.SetupContentsItemResponse
import gloss.domain.setup.dto.response.SetupContentsResponse
import gloss.domain.setup.dto.response.SetupSessionResponse
import gloss.domain.setup.infrastructure.JdbcSetupMessages
import org.springframework.stereotype.Service
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * A reader adds a book by talking it through: the model answers each message (SetupMessageService), a
 * pick holds the edition's contents in the session for the reader to look over and edit, and starting
 * makes the book from them. No book exists before that. The source is asked between transactions, so a
 * slow answer holds no connection and no lock.
 */
@Service
class SetupSessionService(
    private val setupSessionRepository: SetupSessionRepository,
    private val jdbcSetupMessages: JdbcSetupMessages,
    private val bookSourceService: BookSourceService,
    private val bookService: BookService,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /**
     * Only an edition with contents that one of this session's searches found, so what the reader saw
     * is what is held. A later pick replaces an earlier one.
     */
    fun pick(
        accountId: UUID,
        sessionId: UUID,
        request: SetupPickRequest,
    ): SetupSessionResponse {
        val offered = offered(accountId = accountId, sessionId = sessionId, ref = request.ref, withContents = true)
        val edition = bookSourceService.fetch(accountId, offered.ref)
        // Offered with contents, but the source has none now: picked as offered or not at all.
        val contents = edition.contents ?: throw GlossException(SetupError.CONTENTS_GONE)
        val brought =
            ContentsBrought(
                ref = offered.ref,
                title = edition.title,
                items = ContentsReader.read(contents).size,
                typed = false,
            )
        write {
            // Another tab's pick that landed while this one asked the source is not undone.
            val session =
                held(
                    accountId = accountId,
                    sessionId = sessionId,
                    step = SetupPolicy.Step.PICK,
                    revision = request.revision,
                )
            val now = clock.instant()
            session.choose(editionRef = edition.ref, title = edition.title, contents = contents, at = now)
            setupSessionRepository.saveAndFlush(session)
            jdbcSetupMessages.append(session, ReaderPicked(offered.ref, edition.title), now)
            jdbcSetupMessages.append(session, brought, now)
        }
        return get(accountId, sessionId)
    }

    /**
     * Contents the reader typed in, held as a pick's are: for an edition this session found without
     * contents, or for a book of their own, which with no session yet starts one.
     */
    fun type(
        accountId: UUID,
        sessionId: UUID?,
        request: SetupTypedRequest,
    ): SetupSessionResponse {
        val items = ContentsReader.read(request.toc).size
        val ref = request.ref
        // An edition is one a session's search found, so without a session there is none to type for.
        if (ref != null && sessionId == null) throw GlossException(SetupError.BOOK_NOT_OFFERED)
        if (sessionId != null && request.revision == null) throw GlossException(CommonError.INVALID_INPUT)
        val edition =
            ref?.let {
                offered(accountId = accountId, sessionId = checkNotNull(sessionId), ref = it, withContents = false)
                bookSourceService.fetch(accountId, it)
            }
        val typed = if (edition == null) TypedBook.of(request.title.orEmpty(), request.authors, request.toc) else null
        val title = edition?.title ?: checkNotNull(typed).title
        val brought = ContentsBrought(ref = ref, title = title, items = items, typed = true)
        val id =
            write {
                val now = clock.instant()
                val session =
                    if (sessionId == null) {
                        SetupSession.start(accountId = accountId, now = now)
                    } else {
                        held(
                            accountId = accountId,
                            sessionId = sessionId,
                            step = SetupPolicy.Step.PICK,
                            revision = checkNotNull(request.revision),
                        )
                    }
                if (typed == null) {
                    session.choose(editionRef = checkNotNull(ref), title = title, contents = request.toc, at = now)
                } else {
                    session.type(title = typed.title, authors = typed.authors, contents = typed.toc, at = now)
                }
                setupSessionRepository.saveAndFlush(session)
                jdbcSetupMessages.append(session, ReaderTyped(title), now)
                jdbcSetupMessages.append(session, brought, now)
                session.id
            }
        return get(accountId, id)
    }

    /** Checked as a book's contents are, so what is held can always be made into one. */
    fun edit(
        accountId: UUID,
        sessionId: UUID,
        request: SetupContentsRequest,
    ): SetupSessionResponse {
        ContentsReader.read(request.toc)
        write {
            // Another tab's edit or pick since: saving would overwrite what the reader never saw.
            val session =
                held(
                    accountId = accountId,
                    sessionId = sessionId,
                    step = SetupPolicy.Step.EDIT,
                    revision = request.revision,
                )
            session.edit(contents = request.toc, at = clock.instant())
            setupSessionRepository.saveAndFlush(session)
        }
        return get(accountId, sessionId)
    }

    /**
     * Makes the book from the held contents, written in the level and voice the reader chose, and
     * confirms them through the book's own paths, so the plan is asked for exactly as from the book's
     * page. The edition is read again first; the book is made under the session's lock, so a second
     * start finds the session done and makes nothing.
     */
    fun confirm(
        accountId: UUID,
        sessionId: UUID,
        request: SetupConfirmRequest,
    ): SetupSessionResponse {
        val style = styleOf(request)
        val seen =
            setupSessionRepository.findByIdAndAccountId(sessionId, accountId)
                ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
        // Started already with what the reader saw, as when the answer to their start was lost: done.
        if (startedWith(seen, request.revision)) return get(accountId, sessionId)
        refuseUnless(seen, SetupPolicy.Step.CONFIRM)
        refuseChanged(seen, request.revision)
        val edition = seen.editionRef?.let { bookSourceService.fetch(accountId, it) }
        write {
            val session =
                setupSessionRepository.findForUpdate(sessionId, accountId)
                    ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
            // A second start of the same contents that waited for this lock finds the first one's book.
            if (startedWith(session, request.revision)) return@write
            refuseUnless(session, SetupPolicy.Step.CONFIRM)
            // Edited or picked again while the source was asked: what the reader saw is not what is held.
            refuseChanged(session, request.revision)
            val contents = checkNotNull(session.contents) { "A chosen book's contents are held" }
            val confirmed =
                if (edition != null) {
                    bookSourceService.keep(accountId = accountId, edition = edition, contents = contents, style = style)
                } else {
                    val typed =
                        TypedBook.of(
                            checkNotNull(session.heldTitle),
                            session.typedAuthors.orEmpty().toList(),
                            contents,
                        )
                    bookService.createConfirmed(accountId, typed, style)
                }
            val now = clock.instant()
            session.confirm(bookId = confirmed.bookId, tocId = confirmed.tocId, at = now)
            setupSessionRepository.saveAndFlush(session)
            val said = ReaderConfirmed(tocId = confirmed.tocId, level = style.level.code, voice = style.voice.code)
            jdbcSetupMessages.append(session, said, now)
            jdbcSetupMessages.append(session, SetupDone(bookId = confirmed.bookId, items = confirmed.items), now)
        }
        return get(accountId, sessionId)
    }

    /**
     * The session and its messages as of one moment, read in one short transaction; the held edition
     * is described after it ends, so a slow answer from the source holds no connection.
     */
    fun get(
        accountId: UUID,
        sessionId: UUID,
    ): SetupSessionResponse {
        val (session, messages) =
            checkNotNull(
                snapshot.execute {
                    val session =
                        setupSessionRepository.findByIdAndAccountId(sessionId, accountId)
                            ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
                    session to jdbcSetupMessages.list(session.id, accountId)
                },
            )
        val held =
            heldOf(bookSourceService = bookSourceService, accountId = accountId, session = session, messages = messages)
        return SetupSessionResponse.from(session = session, messages = messages, held = held, now = clock.instant())
    }

    private fun offered(
        accountId: UUID,
        sessionId: UUID,
        ref: String,
        withContents: Boolean,
    ): FoundEdition {
        val session =
            setupSessionRepository.findByIdAndAccountId(sessionId, accountId)
                ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
        refuseUnless(session, SetupPolicy.Step.PICK)
        // Before the source is asked for the edition; checked again under the lock.
        refuseWhileSearching(session, clock.instant())
        val contents = jdbcSetupMessages.list(session.id, accountId).map { it.content }
        return ShownEditions
            .of(contents)
            .firstOrNull { it.ref == ref && it.hasContents == withContents }
            ?: throw GlossException(SetupError.BOOK_NOT_OFFERED)
    }

    /**
     * The session locked for [step], as the reader saw it at [revision]: a request made on an older
     * one, as from another tab or before a lookup that took a while, changes nothing.
     */
    private fun held(
        accountId: UUID,
        sessionId: UUID,
        step: SetupPolicy.Step,
        revision: Int,
    ): SetupSession {
        val session =
            setupSessionRepository.findForUpdate(sessionId, accountId)
                ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
        refuseUnless(session, step)
        refuseChanged(session, revision)
        // A pick or typed contents while a message is answered would land between the question and
        // its answer, and would outlive the message that dropped it.
        if (step == SetupPolicy.Step.PICK) refuseWhileSearching(session, clock.instant())
        return session
    }

    private fun <T : Any> write(block: () -> T): T = checkNotNull(transactionTemplate.execute { block() })

    // PostgreSQL gives a repeatable-read transaction one snapshot, so the session row (JPA) and its
    // messages (JDBC) are read as of the same moment, never a pick that landed between the two.
    private val snapshot =
        TransactionTemplate(checkNotNull(transactionTemplate.transactionManager)).apply {
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
            isReadOnly = true
        }
}

private fun styleOf(request: SetupConfirmRequest): WritingStyle {
    val level = WritingStyle.Level.of(request.level)
    val voice = WritingStyle.Voice.of(request.voice)
    if (level == null || voice == null) throw GlossException(SetupError.INVALID_STYLE)
    return WritingStyle(level, voice)
}

internal fun refuseUnless(
    session: SetupSession,
    step: SetupPolicy.Step,
) {
    if (session.allows(step)) return
    val error =
        when (session.state) {
            SetupPolicy.State.SEARCHING -> SetupError.NO_BOOK_YET
            SetupPolicy.State.BOOK_CHOSEN -> error("Every step is open while a book is chosen")
            SetupPolicy.State.CONFIRMED -> SetupError.ALREADY_CONFIRMED
        }
    throw GlossException(error)
}

internal fun refuseWhileSearching(
    session: SetupSession,
    now: Instant,
) {
    if (session.searching(now)) throw GlossException(SetupError.STILL_SEARCHING)
}

private fun startedWith(
    session: SetupSession,
    revision: Int,
): Boolean = session.state == SetupPolicy.State.CONFIRMED && session.revision == revision

// Every request that changes a session names the revision it saw; an older one changes nothing.
internal fun refuseChanged(
    session: SetupSession,
    revision: Int,
) {
    if (session.revision != revision) throw GlossException(SetupError.SESSION_CHANGED)
}

// Asks the source to describe a held edition, so it is called with no transaction open.
private fun heldOf(
    bookSourceService: BookSourceService,
    accountId: UUID,
    session: SetupSession,
    messages: List<SetupMessage>,
): SetupContentsResponse? {
    val raw = session.contents?.takeIf { session.state == SetupPolicy.State.BOOK_CHOSEN } ?: return null
    val ref = session.editionRef
    val page = ref?.let(bookSourceService::pageOf)
    return SetupContentsResponse(
        ref = ref,
        source = page?.source,
        sourceUrl = page?.url,
        title = checkNotNull(session.heldTitle) { "Held contents come with their title" },
        authors = session.typedAuthors.orEmpty().toList(),
        book =
            ref?.let {
                describe(bookSourceService = bookSourceService, accountId = accountId, messages = messages, ref = it)
            },
        raw = raw,
        items = ContentsReader.read(raw).map(::itemOf),
    )
}

private fun itemOf(item: OutlineItem) =
    SetupContentsItemResponse(
        ord = item.ord,
        parent = item.parent,
        depth = item.depth,
        number = item.number,
        title = item.title,
    )
