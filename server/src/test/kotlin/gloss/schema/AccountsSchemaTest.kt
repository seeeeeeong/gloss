package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AccountsSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun insert(
        loginId: String = "reader-${UUID.randomUUID().toString().take(8)}",
        role: String = "member",
        status: String = "active",
        disabledAt: String? = null,
        displayName: String = "Reader",
    ) = jdbc
        .sql(
            """
            insert into account (id, version, login_id, display_name, password_hash, role, status,
                                 must_change_password, session_epoch, created_at, disabled_at)
            values (:id, 0, :loginId, :displayName, '{bcrypt}x', :role, :status, true, 0, now(),
                    cast(:disabledAt as timestamptz))
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("loginId", loginId)
        .param("displayName", displayName)
        .param("role", role)
        .param("status", status)
        .param("disabledAt", disabledAt)
        .update()

    @Test
    fun `a valid account is stored`() {
        assertThatCode { insert() }.doesNotThrowAnyException()
    }

    @Test
    fun `a login id is taken once`() {
        insert(loginId = "taken-once")

        assertThatThrownBy { insert(loginId = "taken-once") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a login id that the policy would not produce is refused`() {
        assertThatThrownBy { insert(loginId = "Upper") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(loginId = "ab") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an unknown role or status is refused`() {
        assertThatThrownBy { insert(role = "admin") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(status = "deleted") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a disabled account records when, and an active one does not`() {
        assertThatThrownBy { insert(status = "disabled") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(disabledAt = "2026-10-04T00:00:00Z") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatCode { insert(status = "disabled", disabledAt = "2026-10-04T00:00:00Z") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `a display name is 1 to 50 characters`() {
        assertThatThrownBy { insert(displayName = "") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(displayName = "가".repeat(51)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
