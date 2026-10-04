package gloss.account

import gloss.account.domain.Account
import gloss.account.domain.AccountRepository
import gloss.common.UuidV7
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AccountService(
    private val accounts: AccountRepository,
    private val encoder: PasswordEncoder,
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
        accounts.lockFirstOperatorCreation()
        if (accounts.existsByRole(AccountRole.OPERATOR)) return false
        check(password.isNotEmpty()) { "The first operator's password is required" }
        val normalized = checkNotNull(LoginIdPolicy.normalize(loginId)) { "The first operator's login id is invalid" }
        check(PasswordPolicy.check(password) == PasswordDecision.Accepted) {
            "The first operator's password does not meet the password policy"
        }
        val now = clock.instant()
        accounts.save(
            Account(
                id = UuidV7.at(now),
                loginId = normalized,
                displayName = displayName,
                passwordHash = hash(password),
                role = AccountRole.OPERATOR,
                mustChangePassword = true,
                createdBy = null,
                createdAt = now,
            ),
        )
        return true
    }

    @Transactional
    fun authenticate(
        loginId: String,
        password: String,
    ): LoginResult {
        val account = LoginIdPolicy.normalize(loginId)?.let { accounts.findByLoginId(it) }
        val tooLong = PasswordPolicy.check(password) == PasswordDecision.TooLong
        val matches = encoder.matches(if (tooLong) "" else password, account?.passwordHash ?: dummyHash)
        val signedIn = account?.takeIf { matches && !tooLong && it.isActive } ?: return LoginResult.Failed
        accounts.recordLogin(signedIn.id, clock.instant())
        return LoginResult.Succeeded(signedIn.toSummary(), signedIn.sessionEpoch)
    }

    fun get(id: UUID): AccountSummary = accounts.findById(id).orElseThrow().toSummary()

    @Transactional
    fun changePassword(
        id: UUID,
        current: String,
        new: String,
    ): PasswordChangeResult {
        val account = checkNotNull(accounts.findForUpdate(id))
        val tooLong = PasswordPolicy.check(current) == PasswordDecision.TooLong
        if (tooLong || !encoder.matches(current, account.passwordHash)) return PasswordChangeResult.WrongPassword
        val decision = PasswordPolicy.check(new)
        if (decision != PasswordDecision.Accepted) return PasswordChangeResult.Rejected(decision)
        // Otherwise the temporary password could be kept and the forced change still lifted.
        if (new == current) return PasswordChangeResult.SameAsCurrent
        account.changePassword(hash(new), clock.instant())
        return PasswordChangeResult.Changed(account.sessionEpoch)
    }

    private fun hash(password: String): String = checkNotNull(encoder.encode(password))
}
