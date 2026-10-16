package gloss.domain.setup.service

import gloss.common.error.CommonError
import gloss.common.error.GlossException
import gloss.domain.setup.domain.ReaderSaid
import gloss.domain.setup.domain.ReaderText
import gloss.domain.setup.domain.SetupPolicy
import gloss.domain.setup.domain.SetupSession
import gloss.domain.setup.domain.repository.SetupSessionRepository
import gloss.domain.setup.dto.request.SetupMessageRequest
import gloss.domain.setup.dto.response.SetupSessionResponse
import gloss.domain.setup.infrastructure.JdbcSetupMessages
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * What a reader says in a setup session: kept at once, then answered by the model while the reader's
 * page waits on the session. One at a time: a message while the last one is still being answered is
 * refused, so answers are kept in the order of their messages.
 */
@Service
class SetupMessageService(
    private val setupSessionRepository: SetupSessionRepository,
    private val jdbcSetupMessages: JdbcSetupMessages,
    private val setupSessionService: SetupSessionService,
    private val setupTurnDispatcher: SetupTurnDispatcher,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** A session starts with the reader's first message, so opening the screen alone keeps nothing. */
    fun start(
        accountId: UUID,
        request: SetupMessageRequest,
    ): SetupSessionResponse {
        val text = ReaderText.of(request.text) ?: throw GlossException(SetupError.INVALID_TEXT)
        val (sessionId, since) =
            write {
                val now = clock.instant()
                val session = SetupSession.start(accountId = accountId, now = now)
                setupSessionRepository.saveAndFlush(session)
                said(session, text, now)
            }
        setupTurnDispatcher.dispatch(accountId, sessionId, since)
        return setupSessionService.get(accountId, sessionId)
    }

    /** After a pick too: it drops the pick, and the model answers the message. */
    fun say(
        accountId: UUID,
        sessionId: UUID,
        request: SetupMessageRequest,
    ): SetupSessionResponse {
        val text = ReaderText.of(request.text) ?: throw GlossException(SetupError.INVALID_TEXT)
        val revision = request.revision ?: throw GlossException(CommonError.INVALID_INPUT)
        val (_, since) =
            write {
                val session = locked(accountId, sessionId)
                refuseUnless(session, SetupPolicy.Step.SAY)
                refuseChanged(session, revision)
                val now = clock.instant()
                refuseWhileSearching(session, now)
                said(session, text, now)
            }
        setupTurnDispatcher.dispatch(accountId, sessionId, since)
        return setupSessionService.get(accountId, sessionId)
    }

    private fun locked(
        accountId: UUID,
        sessionId: UUID,
    ): SetupSession =
        setupSessionRepository.findForUpdate(sessionId, accountId) ?: throw GlossException(SetupError.SESSION_NOT_FOUND)

    private fun said(
        session: SetupSession,
        text: ReaderText,
        now: Instant,
    ): Pair<UUID, Instant> {
        session.say(now)
        setupSessionRepository.saveAndFlush(session)
        jdbcSetupMessages.append(session, ReaderSaid(text.value), now)
        return session.id to checkNotNull(session.searchingSince)
    }

    private fun <T : Any> write(block: () -> T): T = checkNotNull(transactionTemplate.execute { block() })
}
