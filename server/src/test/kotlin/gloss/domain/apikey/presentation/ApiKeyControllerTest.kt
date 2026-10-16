package gloss.domain.apikey.presentation

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.apikey.domain.KeyCheckResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FakeKeyCheckGateway.Config::class)
class ApiKeyControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val fakeKeyCheckGateway: FakeKeyCheckGateway,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    // Not a real key: the fake gateway accepts anything well formed.
    private val typed = "sk-0123456789abcdef"

    @BeforeEach
    fun reset() {
        fakeKeyCheckGateway.reset()
    }

    private fun signedIn() = Browser(port).also { it.signIn(members.create(), members.password) }

    @Test
    fun `a key is saved, shown by its last four, and removed`() {
        val browser = signedIn()

        val saved = browser.put("/api/me/api-key", mapOf("key" to typed))
        val shown = browser.get("/api/me/api-key")
        val removed = browser.delete("/api/me/api-key")

        assertThat(saved.status).isEqualTo(200)
        assertThat(saved.body.toString()).doesNotContain("0123456789ab")
        assertThat(shown.body?.get("last4")?.asString()).isEqualTo("cdef")
        assertThat(shown.body?.get("status")?.asString()).isEqualTo("valid")
        assertThat(removed.status).isEqualTo(204)
        assertThat(browser.get("/api/me/api-key").code).isEqualTo("apikey.not_registered")
    }

    @Test
    fun `a key DeepSeek refuses is answered with why`() {
        val browser = signedIn()
        fakeKeyCheckGateway.next = KeyCheckResult.Rejected

        val response = browser.put("/api/me/api-key", mapOf("key" to typed))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.code).isEqualTo("apikey.key_rejected")
    }

    @Test
    fun `no one signed out reaches a key`() {
        assertThat(Browser(port).get("/api/me/api-key").status).isEqualTo(401)
    }
}
