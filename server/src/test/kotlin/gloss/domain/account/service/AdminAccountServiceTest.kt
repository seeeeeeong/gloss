package gloss.domain.account.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.account.dto.AccountCreateRequest
import gloss.domain.account.dto.LoginRequest
import gloss.domain.account.dto.PasswordChangeRequest
import gloss.domain.account.exception.AccountError
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class AdminAccountServiceTest(
    @Autowired private val adminAccountService: AdminAccountService,
    @Autowired private val accountService: AccountService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val password = "correct horse battery"
    private lateinit var operatorId: UUID

    @BeforeEach
    fun operator() {
        clock.reset()
        accountService.ensureFirstOperator("root", password, "Operator")
        operatorId = accountService.login(LoginRequest("root", password), IP).me.id
    }

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private fun column(
        name: String,
        loginId: String,
    ): Any? {
        entityManager.flush()
        return jdbc
            .sql("select $name from account where login_id = :loginId")
            .param("loginId", loginId)
            .query { rs, _ -> rs.getObject(1) }
            .single()
    }

    private fun create(
        loginId: String = "reader",
        role: String = "member",
    ) = adminAccountService.create(operatorId, AccountCreateRequest(loginId, "Reader", role))

    @Test
    fun `a new account signs in with its temporary password and must change it`() {
        val created = create()

        val me = accountService.login(LoginRequest("reader", created.temporaryPassword), IP).me
        assertThat(me.mustChangePassword).isTrue()
        assertThat(me.role).isEqualTo("member")
        assertThat(column("created_by", "reader")).isEqualTo(operatorId)
    }

    @Test
    fun `a login id is taken once, and a malformed one is refused`() {
        create()

        assertFails(AccountError.LOGIN_ID_TAKEN) { create(loginId = "Reader") }
        assertFails(AccountError.INVALID_LOGIN_ID) { create(loginId = "a b") }
    }

    @Test
    fun `a display name of only non-breaking spaces is refused, not stored empty`() {
        assertFails(AccountError.INVALID_DISPLAY_NAME) {
            adminAccountService.create(operatorId, AccountCreateRequest("reader", "\u00A0\u2007", "member"))
        }
    }

    @Test
    fun `an unknown role is refused`() {
        assertFails(AccountError.INVALID_ROLE) { create(role = "admin") }
    }

    @Test
    fun `a reset hands out a new temporary password and ends the account's sessions`() {
        val created = create()
        val id = accountService.login(LoginRequest("reader", created.temporaryPassword), IP).me.id
        accountService.changePassword(id, PasswordChangeRequest(created.temporaryPassword, "a new and longer password"))

        val reset = adminAccountService.resetPassword(id)

        assertThat(column("session_epoch", "reader")).isEqualTo(2)
        assertThat(column("must_change_password", "reader")).isEqualTo(true)
        assertThat(accountService.login(LoginRequest("reader", reset.temporaryPassword), IP).me.loginId)
            .isEqualTo("reader")
    }

    @Test
    fun `a disabled account cannot sign in until enabled again`() {
        val created = create()
        val id = accountService.login(LoginRequest("reader", created.temporaryPassword), IP).me.id

        adminAccountService.disable(id)

        assertThat(column("session_epoch", "reader")).isEqualTo(1)
        assertFails(AccountError.LOGIN_FAILED) {
            accountService.login(LoginRequest("reader", created.temporaryPassword), IP)
        }
        adminAccountService.enable(id)
        assertThat(accountService.login(LoginRequest("reader", created.temporaryPassword), IP).me.loginId)
            .isEqualTo("reader")
    }

    @Test
    fun `the last active operator cannot be disabled`() {
        assertFails(AccountError.LAST_OPERATOR) { adminAccountService.disable(operatorId) }
    }

    @Test
    fun `one of two operators can be disabled`() {
        val created = create(loginId = "second", role = "operator")
        val second = accountService.login(LoginRequest("second", created.temporaryPassword), IP).me.id

        adminAccountService.disable(second)

        assertThat(column("status", "second")).isEqualTo("disabled")
    }

    @Test
    fun `an unknown account is not found`() {
        assertFails(AccountError.ACCOUNT_NOT_FOUND) { adminAccountService.resetPassword(UUID.randomUUID()) }
    }

    @Test
    fun `the list shows every account, newest first`() {
        clock.advance(Duration.ofMinutes(1))
        create()

        assertThat(adminAccountService.list().map { it.loginId }).containsExactly("reader", "root")
    }

    private companion object {
        const val IP = "203.0.113.8"
    }
}
