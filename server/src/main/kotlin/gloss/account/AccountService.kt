package gloss.account

import gloss.account.domain.Account
import gloss.account.domain.AccountRepository
import gloss.account.domain.LoginIdPolicy
import gloss.account.domain.PasswordDecision
import gloss.account.domain.PasswordPolicy
import gloss.account.dto.LoginRequest
import gloss.account.dto.MeResponse
import gloss.account.dto.PasswordChangeRequest
import gloss.account.dto.SignedInAccount
import gloss.common.GlossException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
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

    @Transactional
    fun login(request: LoginRequest): SignedInAccount {
        val account = LoginIdPolicy.normalize(request.loginId)?.let { accountRepository.findByLoginId(it) }
        val password = request.password
        val tooLong = PasswordPolicy.check(password) == PasswordDecision.TooLong
        val matches = passwordEncoder.matches(if (tooLong) "" else password, account?.passwordHash ?: dummyHash)
        val signedIn =
            account?.takeIf { matches && !tooLong && it.isActive } ?: throw GlossException(AccountError.LOGIN_FAILED)
        accountRepository.recordLogin(signedIn.id, clock.instant())
        return SignedInAccount(MeResponse.from(signedIn), signedIn.role, signedIn.sessionEpoch)
    }

    fun getMe(id: UUID): MeResponse = MeResponse.from(accountRepository.findById(id).orElseThrow())

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

                else -> AccountError.of(PasswordPolicy.check(new))
            }
        if (error != null) throw GlossException(error)
        account.changePassword(hash(new), clock.instant())
        return account.sessionEpoch
    }

    private fun hash(password: String): String = checkNotNull(passwordEncoder.encode(password))
}
