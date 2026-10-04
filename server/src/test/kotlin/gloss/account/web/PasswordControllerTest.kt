package gloss.account.web

import gloss.TestcontainersConfiguration
import gloss.account.Browser
import gloss.account.Members
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
class PasswordControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)
    private val newPassword = "a new and longer password"

    private fun change(
        browser: Browser,
        current: String = members.password,
        new: String = newPassword,
    ) = browser.put("/api/me/password", mapOf("currentPassword" to current, "newPassword" to new))

    @Test
    fun `changing the password keeps this session and ends the others`() {
        val loginId = members.create()
        val here = Browser(port).apply { signIn(loginId, members.password) }
        val elsewhere = Browser(port).apply { signIn(loginId, members.password) }

        assertThat(change(here).status).isEqualTo(204)

        assertThat(here.get("/api/auth/me").status).isEqualTo(200)
        assertThat(elsewhere.get("/api/auth/me").status).isEqualTo(401)
        assertThat(Browser(port).signIn(loginId, newPassword).status).isEqualTo(200)
    }

    @Test
    fun `the session keeps its CSRF token through the change`() {
        val browser = Browser(port).apply { signIn(members.create(), members.password) }

        change(browser)

        assertThat(browser.post("/api/auth/logout").status).isEqualTo(204)
    }

    @Test
    fun `a current password past 72 bytes is refused, even when its first 72 bytes match`() {
        val long = "a".repeat(72)
        val browser = Browser(port).apply { signIn(members.create(), members.password) }
        change(browser, new = long)

        assertThat(change(browser, current = long + "anything", new = newPassword).code)
            .isEqualTo("wrong_password:account")
    }

    @Test
    fun `a wrong current password is refused`() {
        val browser = Browser(port).apply { signIn(members.create(), members.password) }

        val response = change(browser, current = "wrong password here")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.code).isEqualTo("wrong_password:account")
    }

    @Test
    fun `a new password the policy refuses is named`() {
        val browser = Browser(port).apply { signIn(members.create(), members.password) }

        assertThat(change(browser, new = "short").code).isEqualTo("password_too_short:account")
        assertThat(change(browser, new = "a".repeat(73)).code).isEqualTo("password_too_long:account")
        assertThat(change(browser, new = members.password).code).isEqualTo("password_unchanged:account")
    }
}
