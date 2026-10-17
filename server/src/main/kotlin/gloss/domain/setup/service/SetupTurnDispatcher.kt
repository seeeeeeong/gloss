package gloss.domain.setup.service

import gloss.common.util.traceOf
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore

/**
 * Starts the model's answer to a reader's message, or its look for an edition's contents, and returns
 * at once: the page looks again until the session stops waiting, so no request is held open for the
 * minute a turn may take. A turn a restart cuts short stops holding the session back at its deadline.
 */
@Service
class SetupTurnDispatcher(
    private val setupTurnService: SetupTurnService,
    private val setupContentsTurnService: SetupContentsTurnService,
    private val setupWebSearch: SetupWebSearch,
    @Value("\${gloss.setup.async-turns}") private val async: Boolean,
    @Value("\${gloss.setup.turns-at-once}") turnsAtOnce: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val slots = Semaphore(turnsAtOnce)

    /** Whether contents may be looked for: without the operator's search key no page can be found. */
    val seeks: Boolean get() = setupWebSearch.available

    fun dispatch(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
    ) = run(accountId, sessionId, since) { setupTurnService.answer(accountId, sessionId, since) }

    /** The look for the held edition's detailed contents, started [since]. */
    fun dispatchSeek(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
    ) = run(accountId, sessionId, since) { setupContentsTurnService.seek(accountId, sessionId, since) }

    @Suppress("TooGenericExceptionCaught")
    private fun run(
        accountId: UUID,
        sessionId: UUID,
        since: Instant,
        turn: () -> Unit,
    ) {
        if (!async) return turn()
        if (!slots.tryAcquire()) {
            return setupTurnService.refuse(
                accountId = accountId,
                sessionId = sessionId,
                since = since,
                error = SetupError.TURNS_BUSY,
            )
        }
        Thread.ofVirtual().name("setup-turn").start {
            try {
                turn()
            } catch (e: Exception) {
                log.warn("A setup turn could not be ended{}", traceOf(e))
            } finally {
                slots.release()
            }
        }
    }
}
