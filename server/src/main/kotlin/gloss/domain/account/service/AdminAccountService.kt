package gloss.domain.account.service

import gloss.common.error.GlossException
import gloss.domain.account.dto.AccountCreateRequest
import gloss.domain.account.dto.AdminAccountResponse
import gloss.domain.account.dto.CreatedAccountResponse
import gloss.domain.account.dto.TemporaryPasswordResponse
import gloss.domain.account.entity.Account
import gloss.domain.account.entity.AccountDraft
import gloss.domain.account.entity.AccountRole
import gloss.domain.account.entity.AccountStatus
import gloss.domain.account.exception.AccountError
import gloss.domain.account.policy.DisplayNamePolicy
import gloss.domain.account.policy.LoginIdPolicy
import gloss.domain.account.policy.TemporaryPasswordPolicy
import gloss.domain.account.repository.AccountRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** What an operator does to accounts. Who may call it is SecurityConfig's to decide. */
@Service
@Transactional
class AdminAccountService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(): List<AdminAccountResponse> =
        accountRepository.findAllByOrderByCreatedAtDesc().map(AdminAccountResponse::from)

    fun create(
        operatorId: UUID,
        request: AccountCreateRequest,
    ): CreatedAccountResponse {
        val draft = draftOf(request)
        if (accountRepository.existsByLoginId(draft.loginId)) throw GlossException(AccountError.LOGIN_ID_TAKEN)
        val temporaryPassword = TemporaryPasswordPolicy.generate()
        val account =
            Account.createByOperator(
                draft = draft,
                temporaryHash = hash(temporaryPassword),
                createdBy = operatorId,
                now = clock.instant(),
            )
        try {
            accountRepository.saveAndFlush(account)
        } catch (_: DataIntegrityViolationException) {
            // Another operator took the same login id between the check and the insert.
            throw GlossException(AccountError.LOGIN_ID_TAKEN)
        }
        return CreatedAccountResponse(AdminAccountResponse.from(account), temporaryPassword)
    }

    fun resetPassword(id: UUID): TemporaryPasswordResponse {
        val account = find(id)
        val temporaryPassword = TemporaryPasswordPolicy.generate()
        account.resetPassword(hash(temporaryPassword), clock.instant())
        return TemporaryPasswordResponse(temporaryPassword)
    }

    fun disable(id: UUID) {
        accountRepository.lockOperators()
        val account = find(id)
        val lastOperator =
            account.role == AccountRole.OPERATOR &&
                account.isActive &&
                accountRepository.countByRoleAndStatus(AccountRole.OPERATOR, AccountStatus.ACTIVE) <= 1
        if (lastOperator) throw GlossException(AccountError.LAST_OPERATOR)
        account.disable(clock.instant())
    }

    fun enable(id: UUID) {
        find(id).enable()
    }

    private fun draftOf(request: AccountCreateRequest): AccountDraft {
        val loginId = LoginIdPolicy.normalize(request.loginId) ?: throw GlossException(AccountError.INVALID_LOGIN_ID)
        val displayName = DisplayNamePolicy.normalize(request.displayName)
        val role = AccountRole.of(request.role)
        val error =
            when {
                displayName == null -> AccountError.INVALID_DISPLAY_NAME
                role == null -> AccountError.INVALID_ROLE
                else -> return AccountDraft(loginId, displayName, role)
            }
        throw GlossException(error)
    }

    private fun find(id: UUID): Account =
        accountRepository.findById(id).orElseThrow { GlossException(AccountError.ACCOUNT_NOT_FOUND) }

    private fun hash(password: String): String = checkNotNull(passwordEncoder.encode(password))
}
