package gloss.domain.budget.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class UsageControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @Test
    fun `a reader sees this month's usage and sets a limit`() {
        val browser = signedIn()

        val before = browser.get("/api/me/usage")
        val set = browser.put("/api/me/usage/limit", mapOf("monthlyLimitMicros" to 5_000_000))

        assertThat(before.status).isEqualTo(200)
        assertThat(before.body?.get("limitMicros")?.isNull).isTrue()
        assertThat(before.body?.get("spentMicros")?.asLong()).isZero()
        assertThat(set.body?.get("limitMicros")?.asLong()).isEqualTo(5_000_000)
        assertThat(set.body?.get("remainingMicros")?.asLong()).isEqualTo(5_000_000)
    }

    @Test
    fun `a limit of nothing is answered with why`() {
        val response = signedIn().put("/api/me/usage/limit", mapOf("monthlyLimitMicros" to 0))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.code).isEqualTo("budget.invalid_limit")
    }

    @Test
    fun `a reader who has made no calls sees none`() {
        val response = signedIn().get("/api/me/usage/calls")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body?.isArray).isTrue()
        assertThat(response.body?.size()).isZero()
    }

    @Test
    fun `no one signed out sees usage`() {
        assertThat(Browser(port).get("/api/me/usage").status).isEqualTo(401)
        assertThat(Browser(port).get("/api/me/usage/calls").status).isEqualTo(401)
    }
}
