package gloss

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(
    private val start: Instant = Instant.parse("2026-10-04T00:00:00Z"),
) : Clock() {
    private var now = start

    fun advance(by: Duration) {
        now = now.plus(by)
    }

    fun set(at: Instant) {
        now = at
    }

    /** The bean is shared across tests; each test that moves it starts here. */
    fun reset() {
        now = start
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun mutableClock() = MutableClock()
    }
}
