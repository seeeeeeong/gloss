package gloss.domain.setup.service

import gloss.common.error.GlossException
import gloss.domain.setup.domain.SetupContent
import gloss.domain.setup.domain.SetupSession
import gloss.domain.setup.domain.SetupTurnPolicy
import gloss.domain.setup.domain.repository.SetupSessionRepository
import gloss.domain.setup.infrastructure.JdbcSetupMessages
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Keeps what a model turn wrote, each write under the session's lock and only while the session still
 * waits on the request that started the turn: once a newer one took its place, nothing is written.
 */
@Service
class SetupTurnWriter(
    private val setupSessionRepository: SetupSessionRepository,
    private val jdbcSetupMessages: JdbcSetupMessages,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /**
     * Appends [contents] in order; [done] ends the turn. [change] runs first, under the same lock, for
     * what the turn changes in the session itself.
     */
    fun keep(
        asked: TurnAsked,
        contents: List<SetupContent>,
        done: Boolean,
        change: (SetupSession, Instant) -> Unit = { _, _ -> },
    ) {
        transactionTemplate.execute {
            val session =
                setupSessionRepository.findForUpdate(asked.sessionId, asked.accountId)
                    ?: throw GlossException(SetupError.SESSION_NOT_FOUND)
            if (session.searchingSince != asked.since) throw TurnSuperseded()
            val now = clock.instant()
            change(session, now)
            if (done) session.answered()
            setupSessionRepository.saveAndFlush(session)
            contents.forEach { content -> jdbcSetupMessages.append(session, content, now) }
        }
    }

    fun checkDeadline(asked: TurnAsked) {
        if (!clock.instant().isBefore(asked.deadline)) throw GlossException(SetupError.TURN_TIMED_OUT)
    }

    fun pastDeadline(asked: TurnAsked): Boolean = !clock.instant().isBefore(asked.deadline)
}

/** The request a turn answers: the session's, sent [since], and the moment the turn must end by. */
class TurnAsked(
    val accountId: UUID,
    val sessionId: UUID,
    val since: Instant,
) {
    val deadline: Instant = since.plus(SetupTurnPolicy.DEADLINE)
}

/** A newer request took the turn's place: what it has is no longer asked for. */
class TurnSuperseded : RuntimeException() {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
