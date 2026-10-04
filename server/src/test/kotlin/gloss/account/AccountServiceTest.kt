package gloss.account

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.account.domain.Account
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
    @Autowired private val accounts: AccountService,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val password = "correct horse battery"

    private fun operator(): AccountSummary {
        accounts.ensureFirstOperator("root", password, "Operator")
        return (accounts.authenticate("root", password) as LoginResult.Succeeded).account
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
        assertThat(accounts.ensureFirstOperator("Root", password, "Operator")).isTrue()
        assertThat(accounts.ensureFirstOperator("other", password, "Other")).isFalse()

        val account = operator()
        assertThat(account.loginId).isEqualTo("root")
        assertThat(account.role).isEqualTo(AccountRole.OPERATOR)
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
                        accounts.ensureFirstOperator(loginId, password, "Operator")
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
        accounts.ensureFirstOperator("root", password, "Operator")

        assertThat(accounts.ensureFirstOperator("root", "", "Operator")).isFalse()
    }

    @Test
    fun `without an operator, a missing bootstrap password stops the start`() {
        assertThatThrownBy { accounts.ensureFirstOperator("root", "", "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a password change keeps a sign-in recorded after the account was read`() {
        val account = operator()
        jdbc.sql("update account set last_login_at = null where login_id = 'root'").update()
        entityManager.clear()
        entityManager.find(Account::class.java, account.id)

        accounts.authenticate("root", password)
        accounts.changePassword(account.id, password, "a new and longer password")

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
                    pool.submit<PasswordChangeResult> {
                        start.await()
                        accounts.changePassword(id, password, new)
                    }
                }
            start.countDown()

            assertThat(runs.map { it.get() })
                .containsExactlyInAnyOrder(PasswordChangeResult.Changed(1), PasswordChangeResult.WrongPassword)
        } finally {
            pool.shutdown()
            jdbc.sql("delete from account where role = 'operator'").update()
        }
    }

    @Test
    fun `a first operator the policies refuse stops the start`() {
        assertThatThrownBy { accounts.ensureFirstOperator("ab", password, "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { accounts.ensureFirstOperator("root", "short", "Operator") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `the password is stored as a bcrypt hash`() {
        operator()

        assertThat(column("password_hash") as String).startsWith("{bcrypt}\$2a\$12\$")
    }

    @Test
    fun `signing in matches the login id case-insensitively and records when`() {
        accounts.ensureFirstOperator("root", password, "Operator")

        val result = accounts.authenticate("  ROOT ", password)

        assertThat(result).isInstanceOf(LoginResult.Succeeded::class.java)
        assertThat((column("last_login_at") as Timestamp).toInstant())
            .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"))
    }

    @Test
    fun `signing in leaves the version alone, so sign-ins at the same moment do not collide`() {
        accounts.ensureFirstOperator("root", password, "Operator")

        accounts.authenticate("root", password)

        assertThat(column("version")).isEqualTo(0L)
    }

    @Test
    fun `a wrong password, an unknown id and a disabled account fail alike`() {
        accounts.ensureFirstOperator("root", password, "Operator")
        jdbc
            .sql(
                """
                insert into account
                select gen_random_uuid(), 0, 'gone', 'Gone', password_hash, 'member', 'disabled',
                       false, 0, null, now(), null, null, now()
                from account where login_id = 'root'
                """.trimIndent(),
            ).update()

        assertThat(accounts.authenticate("root", "wrong password here")).isEqualTo(LoginResult.Failed)
        assertThat(accounts.authenticate("nobody", password)).isEqualTo(LoginResult.Failed)
        assertThat(accounts.authenticate("gone", password)).isEqualTo(LoginResult.Failed)
        assertThat(accounts.authenticate("root", "x".repeat(100))).isEqualTo(LoginResult.Failed)
    }

    @Test
    fun `changing the password ends other sessions and lifts the forced change`() {
        val account = operator()
        clock.advance(Duration.ofHours(1))

        val result = accounts.changePassword(account.id, password, "a new and longer password")

        assertThat(result).isEqualTo(PasswordChangeResult.Changed(sessionEpoch = 1))
        assertThat(column("must_change_password")).isEqualTo(false)
        assertThat(accounts.authenticate("root", "a new and longer password"))
            .isInstanceOf(LoginResult.Succeeded::class.java)
        assertThat(accounts.authenticate("root", password)).isEqualTo(LoginResult.Failed)
    }

    @Test
    fun `the temporary password cannot be set again to lift the forced change`() {
        val account = operator()

        assertThat(accounts.changePassword(account.id, password, password))
            .isEqualTo(PasswordChangeResult.SameAsCurrent)
        assertThat(column("must_change_password")).isEqualTo(true)
    }

    @Test
    fun `a wrong current password or a refused new one changes nothing`() {
        val account = operator()

        assertThat(accounts.changePassword(account.id, "wrong password here", "a new and longer password"))
            .isEqualTo(PasswordChangeResult.WrongPassword)
        assertThat(accounts.changePassword(account.id, password, "too short"))
            .isEqualTo(PasswordChangeResult.Rejected(PasswordDecision.TooShort))
        assertThat(column("session_epoch")).isEqualTo(0)
    }
}
