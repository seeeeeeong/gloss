package gloss.domain.chat.service

import gloss.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ChatTurnsTest {
    private val clock = MutableClock()
    private val turns = ChatTurns(clock)
    private val account = UUID.randomUUID()

    @Test
    fun `a stop that comes first is waiting when its turn starts`() {
        val turnId = UUID.randomUUID()
        turns.stop(account, turnId)

        val flag = turns.start(sessionId = UUID.randomUUID(), accountId = account, turnId = turnId)

        assertThat(flag?.get()).isTrue()
    }

    @Test
    fun `a reader's waiting stops are few, the newest kept`() {
        val ids = (1..50).map { UUID.randomUUID() }
        ids.forEach { turns.stop(account, it) }

        val waiting = ids.filter { turns.waits(account, it) }

        assertThat(waiting).isEqualTo(ids.takeLast(4))
        assertThat(turns.waits(UUID.randomUUID(), ids.last())).isFalse()
    }

    @Test
    fun `an id the page could not have made is not held`() {
        val made = UUID.fromString("0192f2a4-7b1c-7cc0-8a3e-2b4c5d6e7f80")

        turns.stop(account, made)

        assertThat(turns.waits(account, made)).isFalse()
    }

    @Test
    fun `a stop whose turn never came is let go after thirty minutes`() {
        val old = UUID.randomUUID()
        turns.stop(account, old)
        clock.advance(Duration.ofMinutes(31))

        turns.start(sessionId = UUID.randomUUID(), accountId = UUID.randomUUID(), turnId = UUID.randomUUID())

        assertThat(turns.waits(account, old)).isFalse()
    }

    @Test
    fun `a conversation answers one turn at a time, and is free once it ends`() {
        val session = UUID.randomUUID()
        val first = UUID.randomUUID()
        turns.start(session, account, first)

        val second = turns.start(session, account, UUID.randomUUID())
        turns.end(session, account, first)

        assertThat(second).isNull()
        assertThat(turns.start(session, account, UUID.randomUUID())).isNotNull()
    }

    @Test
    fun `a turn under way stops by the id the server gave it`() {
        val issued = UUID.fromString("0192f2a4-7b1c-7cc0-8a3e-2b4c5d6e7f80")
        val flag = turns.start(UUID.randomUUID(), account, issued)

        turns.stop(account, issued)

        assertThat(flag?.get()).isTrue()
    }

    @Test
    fun `a stop racing its turn's start is never lost, whichever comes first`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(300) {
                val session = UUID.randomUUID()
                val turnId = UUID.randomUUID()
                val ready = CountDownLatch(1)
                val started =
                    pool.submit<AtomicBoolean?> {
                        ready.await()
                        turns.start(session, account, turnId)
                    }
                val stopped =
                    pool.submit {
                        ready.await()
                        turns.stop(account, turnId)
                    }
                ready.countDown()
                stopped.get(5, TimeUnit.SECONDS)
                assertThat(started.get(5, TimeUnit.SECONDS)?.get()).isTrue()
                turns.end(session, account, turnId)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
