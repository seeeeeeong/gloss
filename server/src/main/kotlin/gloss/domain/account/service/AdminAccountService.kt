package gloss.domain.account.service

import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.account.dto.AccountCreateRequest
import gloss.domain.account.dto.AdminAccountResponse
import gloss.domain.account.dto.CreatedAccountResponse
import gloss.domain.account.dto.TemporaryPasswordResponse
import gloss.domain.account.entity.Account
import gloss.domain.account.entity.AccountDraft
import gloss.domain.account.entity.AccountRole
import gloss.domain.account.entity.AccountStatus
import gloss.domain.account.event.AccountCreated
import gloss.domain.account.event.AccountDisabled
import gloss.domain.account.event.AccountEnabled
import gloss.domain.account.event.AccountEvent
import gloss.domain.account.event.PasswordReset
import gloss.domain.account.exception.AccountError
import gloss.domain.account.policy.DisplayNamePolicy
import gloss.domain.account.policy.LoginIdPolicy
import gloss.domain.account.policy.TemporaryPasswordPolicy
import gloss.domain.account.repository.AccountRepository
import org.springframework.context.ApplicationEventPublisher
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
    private val applicationEventPublisher: ApplicationEventPublisher,
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
        val now = clock.instant()
        val account =
            Account.createByOperator(
                draft = draft,
                temporaryHash = hash(temporaryPassword),
                createdBy = operatorId,
                now = now,
            )
        try {
            accountRepository.saveAndFlush(account)
        } catch (_: DataIntegrityViolationException) {
            // Another operator took the same login id between the check and the insert.
            throw GlossException(AccountError.LOGIN_ID_TAKEN)
        }
        publish(
            AccountCreated(
                eventId = UuidV7.at(now),
                occurredAt = now,
                accountId = account.id,
                operatorId = operatorId,
                role = account.role.code,
            ),
        )
        return CreatedAccountResponse(AdminAccountResponse.from(account), temporaryPassword)
    }

    fun resetPassword(
        operatorId: UUID,
        id: UUID,
    ): TemporaryPasswordResponse {
        val account = find(id)
        val temporaryPassword = TemporaryPasswordPolicy.generate()
        val now = clock.instant()
        account.resetPassword(hash(temporaryPassword), now)
        publish(PasswordReset(eventId = UuidV7.at(now), occurredAt = now, accountId = id, operatorId = operatorId))
        return TemporaryPasswordResponse(temporaryPassword)
    }

    fun disable(
        operatorId: UUID,
        id: UUID,
    ) {
        accountRepository.lockOperators()
        val account = find(id)
        val lastOperator =
            account.role == AccountRole.OPERATOR &&
                account.isActive &&
                accountRepository.countByRoleAndStatus(AccountRole.OPERATOR, AccountStatus.ACTIVE) <= 1
        if (lastOperator) throw GlossException(AccountError.LAST_OPERATOR)
        val now = clock.instant()
        if (account.disable(now)) {
            publish(
                AccountDisabled(eventId = UuidV7.at(now), occurredAt = now, accountId = id, operatorId = operatorId),
            )
        }
    }

    fun enable(
        operatorId: UUID,
        id: UUID,
    ) {
        if (find(id).enable()) {
            val now = clock.instant()
            publish(AccountEnabled(eventId = UuidV7.at(now), occurredAt = now, accountId = id, operatorId = operatorId))
        }
    }

    private fun publish(event: AccountEvent) = applicationEventPublisher.publishEvent(event)

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
