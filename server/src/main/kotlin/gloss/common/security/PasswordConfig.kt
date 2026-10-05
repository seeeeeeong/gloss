package gloss.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordConfig {
    // Delegating, so a stored hash names its algorithm and a later cost or algorithm can sit beside it.
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder(
            BCRYPT,
            mapOf(BCRYPT to BCryptPasswordEncoder(COST)),
        )

    private companion object {
        const val BCRYPT = "bcrypt"

        // About 250 ms a hash on a 2020s server core; each step doubles it.
        const val COST = 12
    }
}
