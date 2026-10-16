package gloss.domain.account.presentation.security

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
class AccountSessionFilterTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    @Test
    fun `before the first password change only that, sign-out and who-am-i are open`() {
        val browser = Browser(port).apply { signIn(members.create(mustChangePassword = true), members.password) }

        val elsewhere = browser.get("/api/anything")

        assertThat(elsewhere.status).isEqualTo(403)
        assertThat(elsewhere.code).isEqualTo("account.password_change_required")
        assertThat(browser.get("/api/me").status).isEqualTo(200)
    }

    @Test
    fun `after the change the rest of the api opens`() {
        val browser = Browser(port).apply { signIn(members.create(mustChangePassword = true), members.password) }

        browser.put(
            "/api/me/password",
            mapOf("currentPassword" to members.password, "newPassword" to "a new and longer password"),
        )

        assertThat(browser.get("/api/anything").code).isEqualTo("common.not_found")
    }

    @Test
    fun `a session of an account disabled since is ended`() {
        val loginId = members.create()
        val browser = Browser(port).apply { signIn(loginId, members.password) }

        members.disable(loginId)

        assertThat(browser.get("/api/me").status).isEqualTo(401)
    }
}
