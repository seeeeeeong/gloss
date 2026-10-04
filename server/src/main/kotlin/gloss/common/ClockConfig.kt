package gloss.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** The one source of time. Tests replace it and move it instead of sleeping. */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
