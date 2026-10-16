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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class AdminAccountControllerTest(
    @LocalServerPort private val port: Int,
    @Autowired jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private fun operator() = Browser(port).apply { signIn(members.create(role = "operator"), members.password) }

    private fun newLoginId() = "n-" + UUID.randomUUID().toString().take(12)

    @Test
    fun `a member is refused the operator api`() {
        val member = Browser(port).apply { signIn(members.create(), members.password) }

        val response = member.get("/api/admin/accounts")

        assertThat(response.status).isEqualTo(403)
        assertThat(response.code).isEqualTo("common.forbidden")
    }

    @Test
    fun `an operator creates an account and sees its temporary password once`() {
        val loginId = newLoginId()

        val response =
            operator().post(
                "/api/admin/accounts",
                mapOf(
                    "loginId" to loginId,
                    "displayName" to "Reader",
                    "role" to "member",
                ),
            )

        assertThat(response.status).isEqualTo(201)
        assertThat(response.headers["cache-control"]).anyMatch { it.contains("no-store") }
        val temporaryPassword = response.body!!["temporaryPassword"].asString()
        assertThat(response.body["account"]["loginId"].asString()).isEqualTo(loginId)
        assertThat(Browser(port).signIn(loginId, temporaryPassword).body!!["mustChangePassword"].asBoolean()).isTrue()
    }

    @Test
    fun `the list carries no password or hash`() {
        val body = operator().get("/api/admin/accounts").body.toString()

        assertThat(body).doesNotContain("password\"", "passwordHash", "{bcrypt}")
    }

    @Test
    fun `disabling an account ends its session at once`() {
        val loginId = members.create()
        val reader = Browser(port).apply { signIn(loginId, members.password) }
        val id = reader.get("/api/me").body!!["id"].asString()

        assertThat(operator().post("/api/admin/accounts/$id/disable").status).isEqualTo(204)

        assertThat(reader.get("/api/me").status).isEqualTo(401)
    }

    @Test
    fun `a reset hands a new temporary password and ends the reader's session`() {
        val loginId = members.create()
        val reader = Browser(port).apply { signIn(loginId, members.password) }
        val id = reader.get("/api/me").body!!["id"].asString()

        val reset = operator().post("/api/admin/accounts/$id/password-reset")

        assertThat(reset.status).isEqualTo(200)
        assertThat(reset.headers["cache-control"]).anyMatch { it.contains("no-store") }
        assertThat(reader.get("/api/me").status).isEqualTo(401)
        assertThat(Browser(port).signIn(loginId, reset.body!!["temporaryPassword"].asString()).status).isEqualTo(200)
    }
}
