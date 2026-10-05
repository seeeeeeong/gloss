package gloss.common.security

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
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class SessionConfigTest(
    @LocalServerPort private val port: Int,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private fun corrupt(cookie: String) {
        val sessionId = String(Base64.getDecoder().decode(cookie.substringAfter("=").substringBefore(";")))
        jdbc
            .sql(
                """
                update spring_session_attributes set attribute_bytes = '\x00'::bytea
                where session_primary_id = (select primary_id from spring_session where session_id = :id)
                """.trimIndent(),
            ).param("id", sessionId)
            .update()
    }

    private fun Browser.Response.sessionCookie() = setCookie.single { it.startsWith("gloss_session=") }

    @Test
    fun `a session that no longer reads, after a class moved, signs the reader out instead of failing`() {
        val browser = Browser(port)
        corrupt(browser.signIn(members.create(), members.password).sessionCookie())

        assertThat(browser.get("/api/me").status).isEqualTo(401)
    }

    @Test
    fun `signing in again with that cookie works`() {
        val loginId = members.create()
        val browser = Browser(port)
        corrupt(browser.signIn(loginId, members.password).sessionCookie())
        browser.get("/api/me")

        assertThat(browser.signIn(loginId, members.password).status).isEqualTo(200)
        assertThat(browser.get("/api/me").status).isEqualTo(200)
    }
}
