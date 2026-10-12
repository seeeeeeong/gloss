package gloss.book.application

import gloss.MutableClock
import gloss.book.application.BookSourceThrottle.Verdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BookSourceThrottleTest {
    private val clock = MutableClock()

    private fun throttle(
        perMinute: Int = 100,
        perSecond: Int = 100,
        perDay: Int = 100,
    ) = BookSourceThrottle(perMinute = perMinute, perSecond = perSecond, perDay = perDay, clock = clock)

    @Test
    fun `a reader asks as often as a minute allows, then waits for the oldest ask to age out`() {
        val throttle = throttle(perMinute = 2)
        val reader = UUID.randomUUID()

        assertThat(throttle.ask(reader)).isEqualTo(Verdict.ALLOWED)
        clock.advance(Duration.ofSeconds(30))
        assertThat(throttle.ask(reader)).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(reader)).isEqualTo(Verdict.READER_TOO_OFTEN)
        clock.advance(Duration.ofSeconds(30))
        assertThat(throttle.ask(reader)).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(reader)).isEqualTo(Verdict.READER_TOO_OFTEN)
    }

    @Test
    fun `a refused ask is not counted, and one reader's pace does not hold back another`() {
        val throttle = throttle(perMinute = 2)
        val reader = UUID.randomUUID()
        repeat(5) { throttle.ask(reader) }

        clock.advance(Duration.ofMinutes(1))

        assertThat(throttle.ask(reader)).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(reader)).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.ALLOWED)
    }

    @Test
    fun `every reader together stays under what the key allows a second`() {
        val throttle = throttle(perSecond = 2)

        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.ALLOWED)
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.KEY_SPENT)
        clock.advance(Duration.ofSeconds(1))
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.ALLOWED)
    }

    @Test
    fun `every reader together stays under what the key allows a day, which starts over at midnight in Korea`() {
        val throttle = throttle(perDay = 2)
        // 23:59:50 in Korea.
        clock.set(Instant.parse("2026-10-04T14:59:50Z"))
        throttle.ask(UUID.randomUUID())
        clock.advance(Duration.ofSeconds(2))
        throttle.ask(UUID.randomUUID())
        clock.advance(Duration.ofSeconds(2))

        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.KEY_SPENT)
        clock.advance(Duration.ofSeconds(10))
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.ALLOWED)
    }

    @Test
    fun `a reader at their own limit is told so, even when the key is spent as well`() {
        val throttle = throttle(perMinute = 1, perSecond = 1)
        val reader = UUID.randomUUID()
        throttle.ask(reader)

        assertThat(throttle.ask(reader)).isEqualTo(Verdict.READER_TOO_OFTEN)
        assertThat(throttle.ask(UUID.randomUUID())).isEqualTo(Verdict.KEY_SPENT)
    }
}
