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
    private var now: Instant = Instant.parse("2026-10-04T00:00:00Z"),
) : Clock() {
    fun advance(by: Duration) {
        now = now.plus(by)
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
