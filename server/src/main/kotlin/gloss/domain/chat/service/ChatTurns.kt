package gloss.domain.chat.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The turns being answered, one a conversation, and the flag that stops each, named by the id the
 * page gave the turn. Held in memory, as one instance runs: a reader's message that a crash left
 * unanswered holds nothing back. A stop may come before its turn does, and waits for it a while.
 */
@Component
class ChatTurns(
    private val clock: Clock,
) {
    private data class Key(
        val accountId: UUID,
        val turnId: UUID,
    )

    private class Stop(
        val flag: AtomicBoolean,
        val at: Instant,
        val order: Long,
    )

    private val live = ConcurrentHashMap<UUID, Key>()
    private val stops = ConcurrentHashMap<Key, Stop>()
    private val counter = AtomicLong()

    // Start, stop and end each read and change both maps; one at a time, a stop racing a start is never lost.

    /** The new turn's stop flag, set already if the reader stopped it; null when the conversation is being answered. */
    @Synchronized
    fun start(
        sessionId: UUID,
        accountId: UUID,
        turnId: UUID,
    ): AtomicBoolean? {
        forgetOld()
        val key = Key(accountId, turnId)
        if (live.containsKey(sessionId)) return null
        val flag = flagOf(key)
        live[sessionId] = key
        return flag
    }

    /**
     * A turn under way stops by whatever id it has, the server's own included. One not here yet waits
     * only if the page could have made its id, and a reader's waiting stops beyond a few drop the oldest.
     */
    @Synchronized
    fun stop(
        accountId: UUID,
        turnId: UUID,
    ) {
        val key = Key(accountId, turnId)
        if (key in live.values) {
            flagOf(key).set(true)
            return
        }
        if (!isPageMade(turnId)) return
        forgetOld()
        flagOf(Key(accountId, turnId)).set(true)
        val waiting = stops.entries.filter { it.key.accountId == accountId && it.key !in live.values }
        waiting
            .sortedByDescending { it.value.order }
            .drop(MAX_WAITING)
            .forEach { stops.remove(it.key, it.value) }
    }

    @Synchronized
    fun end(
        sessionId: UUID,
        accountId: UUID,
        turnId: UUID,
    ) {
        val key = Key(accountId, turnId)
        live.remove(sessionId, key)
        stops.remove(key)
    }

    /** Whether the conversation has a turn under way. */
    fun isLive(sessionId: UUID): Boolean = live.containsKey(sessionId)

    /** Whether a stop waits for [turnId] of [accountId]; for tests of how many are held. */
    internal fun waits(
        accountId: UUID,
        turnId: UUID,
    ): Boolean = stops.containsKey(Key(accountId, turnId))

    private fun flagOf(key: Key): AtomicBoolean =
        stops.computeIfAbsent(key) { Stop(AtomicBoolean(false), clock.instant(), counter.incrementAndGet()) }.flag

    // A stop whose turn never came; long past any turn's call, so it is a leftover.
    private fun forgetOld() {
        val before = clock.instant().minus(FORGET_AFTER)
        val running = live.values.toSet()
        stops.entries.removeIf { (key, stop) -> key !in running && stop.at.isBefore(before) }
    }

    private companion object {
        val FORGET_AFTER: Duration = Duration.ofMinutes(30)

        // One turn is answered at a time, so a page has one or two stops in flight; the rest is noise.
        const val MAX_WAITING = 4

        // The page names a turn with crypto.randomUUID(): version 4, the IETF variant.
        private const val RANDOM_VERSION = 4
        private const val IETF_VARIANT = 2

        fun isPageMade(turnId: UUID) = turnId.version() == RANDOM_VERSION && turnId.variant() == IETF_VARIANT
    }
}
