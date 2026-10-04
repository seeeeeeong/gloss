package gloss.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    // The one place the real clock is made; everything else injects it.
    @Suppress("ForbiddenMethodCall")
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
