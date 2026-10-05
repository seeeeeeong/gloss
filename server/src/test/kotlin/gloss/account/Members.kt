package gloss.account

import gloss.common.util.UuidV7
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/** Accounts for tests that sign in over HTTP, where nothing is rolled back: each gets a fresh login id. */
class Members(
    private val jdbc: JdbcClient,
    private val encoder: PasswordEncoder,
    private val clock: Clock,
) {
    val password = "correct horse battery"

    fun create(
        mustChangePassword: Boolean = false,
        role: String = "member",
    ): String {
        val loginId = "m-" + UUID.randomUUID().toString().take(12)
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Member', :hash, :role, 'active', :mustChange, 0, :now)
                """.trimIndent(),
            ).param("id", UuidV7.at(clock.instant()))
            .param("now", Timestamp.from(clock.instant()))
            .param("loginId", loginId)
            .param("hash", encoder.encode(password))
            .param("mustChange", mustChangePassword)
            .param("role", role)
            .update()
        return loginId
    }

    fun disable(loginId: String) {
        jdbc
            .sql("update account set status = 'disabled', disabled_at = now() where login_id = :loginId")
            .param("loginId", loginId)
            .update()
    }
}
