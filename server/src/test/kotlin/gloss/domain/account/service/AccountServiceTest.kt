package gloss.domain.account.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.account.dto.LoginRequest
import gloss.domain.account.dto.MeResponse
import gloss.domain.account.dto.PasswordChangeRequest
import gloss.domain.account.entity.Account
import gloss.domain.account.exception.AccountError
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@Import(TestcontainersConfiguration::class, MutableClock.Config::class)
@Transactional
class AccountServiceTest(
    @Autowired private val accountService: AccountService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val password = "correct horse battery"

    private fun operator(): MeResponse {
        accountService.ensureFirstOperator("root", password, "Operator")
        return accountService.login(LoginRequest("root", password)).me
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
        loginId: String = "root",
    ): Any? {
        entityManager.flush()
        return jdbc
            .sql("select $name from account where login_id = :loginId")
            .param("loginId", loginId)
            .query { rs, _ -> rs.getObject(1) }
            .single()
    }

    @Test
    fun `the first operator is created once and must change the password`() {
        assertThat(accountService.ensureFirstOperator("Root", password, "Operator")).isTrue()
        assertThat(accountService.ensureFirstOperator("other", password, "Other")).isFalse()

        val account = operator()
        assertThat(account.loginId).isEqualTo("root")
        assertThat(account.role).isEqualTo("operator")
        assertThat(account.mustChangePassword).isTrue()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two instances starting together make one first operator`() {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val runs =
                listOf("first", "second").map { loginId ->
                    pool.submit<Boolean> {
                        start.await()
                        accountService.ensureFirstOperator(loginId, password, "Operator")
                    }
                }
            start.countDown()

            assertThat(runs.map { it.get() }).containsExactlyInAnyOrder(true, false)
        } finally {
            pool.shutdown()
            jdbc.sql("delete from account where role = 'operator'").update()
        }
    }

    @Test
    fun `once an operator exists, the bootstrap password may be gone`() {
        accountService.ensureFirstOperator("root", password, "Operator")

        assertThat(accountService.ensureFirstOperator("root", "", "Operator")).isFalse()
    }

    @Test
    fun `without an operator, a missing bootstrap password stops the start`() {
        assertThatThrownBy { accountService.ensureFirstOperator("root", "", "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a password change keeps a sign-in recorded after the account was read`() {
        val account = operator()
        jdbc.sql("update account set last_login_at = null where login_id = 'root'").update()
        entityManager.clear()
        entityManager.find(Account::class.java, account.id)

        accountService.login(LoginRequest("root", password))
        accountService.changePassword(account.id, PasswordChangeRequest(password, "a new and longer password"))

        assertThat(column("last_login_at")).isNotNull()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `of two changes from the same password at once, the second is told it is wrong`() {
        val id = operator().id
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val runs =
                listOf("first new password", "second new password").map { new ->
                    pool.submit<Any> {
                        start.await()
                        runCatching { accountService.changePassword(id, PasswordChangeRequest(password, new)) }
                            .getOrElse { (it as GlossException).error }
                    }
                }
            start.countDown()

            assertThat(runs.map { it.get() })
                .containsExactlyInAnyOrder(1, AccountError.WRONG_PASSWORD)
        } finally {
            pool.shutdown()
            jdbc.sql("delete from account where role = 'operator'").update()
        }
    }

    @Test
    fun `a first operator the policies refuse stops the start`() {
        assertThatThrownBy { accountService.ensureFirstOperator("ab", password, "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { accountService.ensureFirstOperator("root", "short", "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `the password is stored as a bcrypt hash`() {
        operator()

        assertThat(column("password_hash") as String).startsWith("{bcrypt}\$2a\$12\$")
    }

    @Test
    fun `signing in matches the login id case-insensitively and records when`() {
        accountService.ensureFirstOperator("root", password, "Operator")

        val signedIn = accountService.login(LoginRequest("  ROOT ", password))

        assertThat(signedIn.me.loginId).isEqualTo("root")
        assertThat((column("last_login_at") as Timestamp).toInstant())
            .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"))
    }

    @Test
    fun `signing in leaves the version alone, so sign-ins at the same moment do not collide`() {
        accountService.ensureFirstOperator("root", password, "Operator")

        accountService.login(LoginRequest("root", password))

        assertThat(column("version")).isEqualTo(0L)
    }

    @Test
    fun `a wrong password, an unknown id and a disabled account fail alike`() {
        accountService.ensureFirstOperator("root", password, "Operator")
        jdbc
            .sql(
                """
                insert into account
                select gen_random_uuid(), 0, 'gone', 'Gone', password_hash, 'member', 'disabled',
                       false, 0, null, now(), null, null, now()
                from account where login_id = 'root'
                """.trimIndent(),
            ).update()

        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", "wrong password here")) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("nobody", password)) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("gone", password)) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", "x".repeat(100))) }
    }

    @Test
    fun `changing the password ends other sessions and lifts the forced change`() {
        val account = operator()
        clock.advance(Duration.ofHours(1))

        val sessionEpoch =
            accountService.changePassword(
                account.id,
                PasswordChangeRequest(password, "a new and longer password"),
            )

        assertThat(sessionEpoch).isEqualTo(1)
        assertThat(column("must_change_password")).isEqualTo(false)
        assertThat(accountService.login(LoginRequest("root", "a new and longer password")).sessionEpoch).isEqualTo(1)
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", password)) }
    }

    @Test
    fun `the temporary password cannot be set again to lift the forced change`() {
        val account = operator()

        assertFails(AccountError.PASSWORD_UNCHANGED) {
            accountService.changePassword(account.id, PasswordChangeRequest(password, password))
        }
        assertThat(column("must_change_password")).isEqualTo(true)
    }

    @Test
    fun `a wrong current password or a refused new one changes nothing`() {
        val account = operator()

        assertFails(AccountError.WRONG_PASSWORD) {
            accountService.changePassword(
                account.id,
                PasswordChangeRequest("wrong password here", "a new and longer password"),
            )
        }
        assertFails(
            AccountError.PASSWORD_TOO_SHORT,
        ) { accountService.changePassword(account.id, PasswordChangeRequest(password, "too short")) }
        assertThat(column("session_epoch")).isEqualTo(0)
    }
}
