package gloss.domain.account.presentation.security

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.UserDetailsService

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class SecurityConfigTest(
    @Autowired private val context: ApplicationContext,
) {
    @Test
    fun `no default user is made, so no generated password is logged`() {
        assertThat(context.getBeanNamesForType(UserDetailsService::class.java)).isEmpty()
    }
}
