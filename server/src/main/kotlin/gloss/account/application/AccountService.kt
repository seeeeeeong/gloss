package gloss.account.application

import gloss.account.application.AccountError
import gloss.account.application.dto.LoginRequest
import gloss.account.application.dto.MyAccountResponse
import gloss.account.application.dto.PasswordChangeRequest
import gloss.account.application.dto.ProfileUpdateRequest
import gloss.account.application.dto.SignedInAccount
import gloss.account.domain.Account
import gloss.account.domain.AccountRepository
import gloss.account.domain.AccountRole
import gloss.account.domain.DisplayName
import gloss.account.domain.LoginId
import gloss.account.domain.LoginThrottleDecision
import gloss.account.domain.LoginThrottlePolicy
import gloss.account.domain.Password
import gloss.account.domain.PasswordDecision
import gloss.account.domain.event.AccountCreated
import gloss.account.domain.event.AccountEvent
import gloss.account.domain.event.PasswordChanged
import gloss.account.domain.event.SignInFailed
import gloss.account.domain.event.SignInFailure
import gloss.account.domain.event.SignedIn
import gloss.account.infrastructure.CommonPasswords
import gloss.account.infrastructure.JdbcLoginFailures
import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import org.springframework.context.ApplicationEventPublisher
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
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        val validLoginId = checkNotNull(LoginId.of(loginId)) { "The first operator's login id is invalid" }
        val validName = checkNotNull(DisplayName.of(displayName)) { "The first operator's display name is invalid" }
        val accepted =
            checkNotNull(Password.check(password) as? PasswordDecision.Accepted) {
                "The first operator's password does not meet the password policy"
            }
        check(!commonPasswords.contains(password)) { "The first operator's password is a common one" }
        val now = clock.instant()
        val account =
            Account.createFirstOperator(
                loginId = validLoginId,
                displayName = validName,
                passwordHash = hash(accepted.password.value),
                now = now,
            )
        accountRepository.save(account)
        publish(
            AccountCreated(
                eventId = UuidV7.at(now),
                occurredAt = now,
                accountId = account.id,
                operatorId = null,
                role = account.role.code,
            ),
        )
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
        val account = LoginId.of(request.loginId)?.let { accountRepository.findByLoginId(it.value) }
        val password = request.password
        val tooLong = Password.check(password) == PasswordDecision.TooLong
        val matches = passwordEncoder.matches(if (tooLong) "" else password, account?.passwordHash ?: dummyHash)
        val signedIn = account?.takeIf { matches && !tooLong && it.isActive }
        if (signedIn == null) {
            jdbcLoginFailures.append(loginIdKey, clientIp, now)
            val reason =
                when {
                    account == null -> SignInFailure.UNKNOWN_LOGIN_ID
                    matches && !tooLong -> SignInFailure.DISABLED
                    else -> SignInFailure.WRONG_PASSWORD
                }
            publish(
                SignInFailed(
                    eventId = UuidV7.at(now),
                    occurredAt = now,
                    accountId = account?.id,
                    ip = clientIp,
                    reason = reason,
                ),
            )
            throw GlossException(AccountError.LOGIN_FAILED)
        }
        accountRepository.recordLogin(signedIn.id, now)
        publish(SignedIn(eventId = UuidV7.at(now), occurredAt = now, accountId = signedIn.id, ip = clientIp))
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
            DisplayName.of(request.displayName) ?: throw GlossException(AccountError.INVALID_DISPLAY_NAME)
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
            Password.check(current) != PasswordDecision.TooLong &&
                passwordEncoder.matches(current, account.passwordHash)
        val error =
            when {
                !currentMatches -> AccountError.WRONG_PASSWORD

                // Otherwise the temporary password could be kept and the forced change still lifted.
                new == current -> AccountError.PASSWORD_UNCHANGED

                else -> AccountError.of(Password.check(new)) ?: commonError(new)
            }
        if (error != null) throw GlossException(error)
        val now = clock.instant()
        account.changePassword(hash(new), now)
        publish(PasswordChanged(eventId = UuidV7.at(now), occurredAt = now, accountId = account.id))
        return account.sessionEpoch
    }

    private fun publish(event: AccountEvent) = applicationEventPublisher.publishEvent(event)

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
