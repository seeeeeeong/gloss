package gloss.domain.account.service

import gloss.common.error.GlossException
import gloss.domain.account.dto.LoginRequest
import gloss.domain.account.dto.MyAccountResponse
import gloss.domain.account.dto.PasswordChangeRequest
import gloss.domain.account.dto.ProfileUpdateRequest
import gloss.domain.account.dto.SignedInAccount
import gloss.domain.account.entity.Account
import gloss.domain.account.entity.AccountRole
import gloss.domain.account.exception.AccountError
import gloss.domain.account.policy.DisplayNamePolicy
import gloss.domain.account.policy.LoginIdPolicy
import gloss.domain.account.policy.LoginThrottleDecision
import gloss.domain.account.policy.LoginThrottlePolicy
import gloss.domain.account.policy.PasswordDecision
import gloss.domain.account.policy.PasswordPolicy
import gloss.domain.account.repository.AccountRepository
import gloss.domain.account.repository.CommonPasswords
import gloss.domain.account.repository.JdbcLoginFailures
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
    private val jdbcLoginFailures: JdbcLoginFailures,
    private val commonPasswords: CommonPasswords,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    // Checked against when the login id is unknown, so a miss takes as long as a wrong password.
    private val dummyHash by lazy { hash("not a password anyone has") }

    @Transactional
    fun ensureFirstOperator(
        loginId: String,
        password: String,
        displayName: String,
    ): Boolean {
        accountRepository.lockFirstOperatorCreation()
        if (accountRepository.existsByRole(AccountRole.OPERATOR)) return false
        check(password.isNotEmpty()) { "The first operator's password is required" }
        val normalized = checkNotNull(LoginIdPolicy.normalize(loginId)) { "The first operator's login id is invalid" }
        check(PasswordPolicy.check(password) == PasswordDecision.Accepted) {
            "The first operator's password does not meet the password policy"
        }
        check(!commonPasswords.contains(password)) { "The first operator's password is a common one" }
        val account =
            Account.createFirstOperator(
                loginId = normalized,
                displayName = displayName,
                passwordHash = hash(password),
                now = clock.instant(),
            )
        accountRepository.save(account)
        return true
    }

    /** A failed sign-in is recorded and kept, though it ends in an exception. */
    @Transactional(noRollbackFor = [GlossException::class])
    fun login(
        request: LoginRequest,
        clientIp: String,
    ): SignedInAccount {
        val now = clock.instant()
        val loginIdKey =
            request.loginId
                .filterNot { it.isISOControl() }
                .trim()
                .lowercase()
                .take(LOGIN_ID_KEY_LENGTH)
                .ifEmpty { BLANK_LOGIN_ID_KEY }
        jdbcLoginFailures.lock(clientIp)
        requireNotThrottled(loginIdKey, clientIp, now)
        val account = LoginIdPolicy.normalize(request.loginId)?.let { accountRepository.findByLoginId(it) }
        val password = request.password
        val tooLong = PasswordPolicy.check(password) == PasswordDecision.TooLong
        val matches = passwordEncoder.matches(if (tooLong) "" else password, account?.passwordHash ?: dummyHash)
        val signedIn = account?.takeIf { matches && !tooLong && it.isActive }
        if (signedIn == null) {
            jdbcLoginFailures.append(loginIdKey, clientIp, now)
            throw GlossException(AccountError.LOGIN_FAILED)
        }
        accountRepository.recordLogin(signedIn.id, now)
        return SignedInAccount(MyAccountResponse.from(signedIn), signedIn.role, signedIn.sessionEpoch)
    }

    private fun requireNotThrottled(
        loginIdKey: String,
        clientIp: String,
        now: Instant,
    ) {
        val since = now.minus(LoginThrottlePolicy.WINDOW)
        val decision =
            LoginThrottlePolicy.decide(
                failuresForLoginIdAndIp = jdbcLoginFailures.countForLoginIdAndIp(loginIdKey, clientIp, since),
                failuresForIp = jdbcLoginFailures.countForIp(clientIp, since),
            )
        if (decision == LoginThrottleDecision.Blocked) throw GlossException(AccountError.TOO_MANY_ATTEMPTS)
    }

    fun getMyAccount(id: UUID): MyAccountResponse = MyAccountResponse.from(accountRepository.findById(id).orElseThrow())

    @Transactional
    fun updateProfile(
        id: UUID,
        request: ProfileUpdateRequest,
    ): MyAccountResponse {
        val displayName =
            DisplayNamePolicy.normalize(request.displayName) ?: throw GlossException(AccountError.INVALID_DISPLAY_NAME)
        val account = accountRepository.findById(id).orElseThrow()
        account.rename(displayName)
        return MyAccountResponse.from(account)
    }

    /** Returns the new session epoch, which ends every other session. */
    @Transactional
    fun changePassword(
        id: UUID,
        request: PasswordChangeRequest,
    ): Int {
        val current = request.currentPassword
        val new = request.newPassword
        val account = checkNotNull(accountRepository.findForUpdate(id))
        val currentMatches =
            PasswordPolicy.check(current) != PasswordDecision.TooLong &&
                passwordEncoder.matches(current, account.passwordHash)
        val error =
            when {
                !currentMatches -> AccountError.WRONG_PASSWORD

                // Otherwise the temporary password could be kept and the forced change still lifted.
                new == current -> AccountError.PASSWORD_UNCHANGED

                else -> AccountError.of(PasswordPolicy.check(new)) ?: commonError(new)
            }
        if (error != null) throw GlossException(error)
        account.changePassword(hash(new), clock.instant())
        return account.sessionEpoch
    }

    private fun commonError(password: String): AccountError? =
        AccountError.PASSWORD_COMMON.takeIf { commonPasswords.contains(password) }

    private fun hash(password: String): String = checkNotNull(passwordEncoder.encode(password))

    private companion object {
        // Matches login_failure.login_id_key, so a long typed id cannot bloat the table.
        const val LOGIN_ID_KEY_LENGTH = 64

        // Bean Validation's blank is Java's whitespace; Kotlin's trim also takes U+00A0 and the like,
        // and control characters, NUL among them, are dropped because PostgreSQL text refuses NUL.
        const val BLANK_LOGIN_ID_KEY = "(blank)"
    }
}
