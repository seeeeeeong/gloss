package gloss.domain.account.service

import gloss.MutableClock
import gloss.TestcontainersConfiguration
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import gloss.domain.account.dto.LoginRequest
import gloss.domain.account.dto.MyAccountResponse
import gloss.domain.account.dto.PasswordChangeRequest
import gloss.domain.account.dto.ProfileUpdateRequest
import gloss.domain.account.entity.Account
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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random

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

    private companion object {
        const val IP = "203.0.113.7"

        // For tests that commit: failures counted per address must not add up across them.
        fun uniqueIp() = "198.18.${Random.nextInt(256)}.${Random.nextInt(1, 255)}"
    }

    private fun operator(): MyAccountResponse {
        accountService.ensureFirstOperator("root", password, "Operator")
        return accountService.login(LoginRequest("root", password), IP).account
    }

    @BeforeEach
    fun resetClock() {
        clock.reset()
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

        accountService.login(LoginRequest("root", password), IP)
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

        val signedIn = accountService.login(LoginRequest("  ROOT ", password), IP)

        assertThat(signedIn.account.loginId).isEqualTo("root")
        assertThat((column("last_login_at") as Timestamp).toInstant())
            .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"))
    }

    @Test
    fun `signing in leaves the version alone, so sign-ins at the same moment do not collide`() {
        accountService.ensureFirstOperator("root", password, "Operator")

        accountService.login(LoginRequest("root", password), IP)

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

        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", "wrong password here"), IP) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("nobody", password), IP) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("gone", password), IP) }
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", "x".repeat(100)), IP) }
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
        assertThat(
            accountService.login(LoginRequest("root", "a new and longer password"), IP).sessionEpoch,
        ).isEqualTo(1)
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("root", password), IP) }
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

    @Test
    fun `five failures from one address block that login id there, the right password too`() {
        operator()
        repeat(5) {
            assertFails(
                AccountError.LOGIN_FAILED,
            ) { accountService.login(LoginRequest("root", "wrong password here"), IP) }
        }

        assertFails(AccountError.TOO_MANY_ATTEMPTS) { accountService.login(LoginRequest("root", password), IP) }
    }

    @Test
    fun `the block lifts after fifteen minutes`() {
        operator()
        repeat(5) { runCatching { accountService.login(LoginRequest("root", "wrong password here"), IP) } }

        clock.advance(Duration.ofMinutes(15).plusSeconds(1))

        assertThat(accountService.login(LoginRequest("root", password), IP).account.loginId).isEqualTo("root")
    }

    @Test
    fun `another address is not blocked`() {
        operator()
        repeat(5) { runCatching { accountService.login(LoginRequest("root", "wrong password here"), IP) } }

        assertThat(
            accountService.login(LoginRequest("root", password), "198.51.100.9").account.loginId,
        ).isEqualTo("root")
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a failure is recorded although the sign-in fails`() {
        val loginId = "ghost-" + UUID.randomUUID().toString().take(8)

        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest(loginId, password), uniqueIp()) }

        val recorded =
            jdbc
                .sql("select count(*) from login_failure where login_id_key = :key")
                .param("key", loginId)
                .query(Int::class.java)
                .single()
        assertThat(recorded).isEqualTo(1)
    }

    @Test
    fun `a login id of only non-breaking spaces fails like any wrong one`() {
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("\u00A0\u2007", password), IP) }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `ten tries at once get five guesses, not ten`() {
        val loginId = "racer-" + UUID.randomUUID().toString().take(8)
        val ip = uniqueIp()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(10)
        try {
            val runs =
                (1..10).map {
                    pool.submit<ErrorCode> {
                        start.await()
                        runCatching { accountService.login(LoginRequest(loginId, password), ip) }
                            .exceptionOrNull()
                            .let { (it as GlossException).error }
                    }
                }
            start.countDown()

            val outcomes = runs.map { it.get() }
            assertThat(outcomes.count { it == AccountError.LOGIN_FAILED }).isEqualTo(5)
            assertThat(outcomes.count { it == AccountError.TOO_MANY_ATTEMPTS }).isEqualTo(5)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `ten different login ids at once from an address near its limit get only what is left`() {
        val ip = uniqueIp()
        repeat(45) {
            jdbc
                .sql("insert into login_failure (login_id_key, ip, attempted_at) values (:key, cast(:ip as inet), :at)")
                .param("key", "earlier-$it")
                .param("ip", ip)
                .param("at", Timestamp.from(clock.instant()))
                .update()
        }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(10)
        try {
            val runs =
                (1..10).map { n ->
                    pool.submit<ErrorCode> {
                        start.await()
                        runCatching { accountService.login(LoginRequest("stuffer-$n", password), ip) }
                            .exceptionOrNull()
                            .let { (it as GlossException).error }
                    }
                }
            start.countDown()

            val outcomes = runs.map { it.get() }
            assertThat(outcomes.count { it == AccountError.LOGIN_FAILED }).isEqualTo(5)
            assertThat(outcomes.count { it == AccountError.TOO_MANY_ATTEMPTS }).isEqualTo(5)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `a login id with a NUL in it fails like any wrong one`() {
        assertFails(AccountError.LOGIN_FAILED) { accountService.login(LoginRequest("reader\u0000x", password), IP) }
    }

    @Test
    fun `renaming to only non-breaking spaces is refused`() {
        val account = operator()

        assertFails(AccountError.INVALID_DISPLAY_NAME) {
            accountService.updateProfile(account.id, ProfileUpdateRequest("\u00A0"))
        }
    }
}
