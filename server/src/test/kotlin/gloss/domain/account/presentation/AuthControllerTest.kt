package gloss.domain.account.presentation

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
class AuthControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired encoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, encoder, clock)

    @Test
    fun `without a session the api answers 401, not a redirect`() {
        val response = Browser(port).get("/api/me")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.code).isEqualTo("common.unauthorized")
    }

    @Test
    fun `signing in without the CSRF token is refused`() {
        val loginId = members.create()

        val response =
            Browser(
                port,
            ).post("/api/auth/login", mapOf("loginId" to loginId, "password" to members.password))

        assertThat(response.status).isEqualTo(403)
        assertThat(response.code).isEqualTo("common.invalid_csrf")
    }

    @Test
    fun `signing in returns the account and keeps the session in an HttpOnly, SameSite=Lax cookie`() {
        val loginId = members.create()
        val browser = Browser(port)

        val response = browser.signIn(loginId, members.password)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body!!["loginId"].asString()).isEqualTo(loginId)
        assertThat(response.body["role"].asString()).isEqualTo("member")
        assertThat(response.body["lastLoginAt"].isNull).isFalse()
        assertThat(response.setCookie.single { it.startsWith("gloss_session=") }).contains("HttpOnly", "SameSite=Lax")
        assertThat(browser.get("/api/me").status).isEqualTo(200)
    }

    @Test
    fun `asking for a CSRF token stores no session`() {
        val response = Browser(port).fetchCsrf()

        assertThat(response.status).isEqualTo(200)
        assertThat(response.setCookie).noneMatch { it.startsWith("gloss_session=") }
    }

    @Test
    fun `the CSRF token is a cookie the page can read`() {
        val cookie = Browser(port).fetchCsrf().setCookie.single { it.startsWith("XSRF-TOKEN=") }

        assertThat(cookie).doesNotContain("HttpOnly").contains("SameSite=Lax")
    }

    @Test
    fun `signing in again starts a new session id`() {
        val loginId = members.create()
        val browser = Browser(port)
        val first = browser.signIn(loginId, members.password).sessionCookie()

        val second = browser.signIn(loginId, members.password).sessionCookie()

        assertThat(second).isNotEqualTo(first)
    }

    private fun Browser.Response.sessionCookie() =
        setCookie.single { it.startsWith("gloss_session=") }.substringBefore(";")

    @Test
    fun `a wrong password and an unknown login id get the same answer`() {
        val loginId = members.create()

        val wrong = Browser(port).signIn(loginId, "wrong password here")
        val unknown = Browser(port).signIn("nobody-here", members.password)

        assertThat(wrong.status).isEqualTo(401)
        assertThat(wrong.body).isEqualTo(unknown.body)
        assertThat(wrong.code).isEqualTo("account.login_failed")
    }

    @Test
    fun `after five failures from one address the sixth try is refused as too many`() {
        val loginId = members.create()
        val browser = Browser(port)
        repeat(5) { browser.signIn(loginId, "wrong password here") }

        val response = browser.signIn(loginId, members.password)

        assertThat(response.status).isEqualTo(429)
        assertThat(response.code).isEqualTo("account.too_many_attempts")
        assertThat(response.headers["retry-after"]?.single()?.toLong()).isBetween(1, 15 * 60)
        assertThat(Browser(port).signIn(loginId, members.password).status).isEqualTo(200)
    }

    @Test
    fun `a blank login id is a validation error, not a failed sign-in`() {
        val browser = Browser(port)
        browser.fetchCsrf()

        val response = browser.post("/api/auth/login", mapOf("loginId" to "", "password" to "x"))

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `signing out ends the session`() {
        val browser = Browser(port)
        browser.signIn(members.create(), members.password)

        assertThat(browser.post("/api/auth/logout").status).isEqualTo(204)
        assertThat(browser.get("/api/me").status).isEqualTo(401)
    }
}
