package gloss.domain.apikey.service

import gloss.common.error.CommonError
import gloss.common.error.GlossException
import gloss.common.util.UuidV7
import gloss.domain.account.service.AccountService
import gloss.domain.apikey.domain.ApiKey
import gloss.domain.apikey.domain.ApiKeySecret
import gloss.domain.apikey.domain.ApiKeyStatus
import gloss.domain.apikey.domain.KeyCipher
import gloss.domain.apikey.domain.KeyProvider
import gloss.domain.apikey.domain.event.ApiKeyEvent
import gloss.domain.apikey.domain.event.ApiKeyRevoked
import gloss.domain.apikey.domain.event.ApiKeySaved
import gloss.domain.apikey.domain.event.KeyRevocation
import gloss.domain.apikey.domain.repository.ApiKeyRepository
import gloss.domain.apikey.dto.response.ApiKeyResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The writes behind [ApiKeyService], each in one short transaction under the account's lock. */
@Component
class ApiKeyStore(
    private val apiKeyRepository: ApiKeyRepository,
    private val accountService: AccountService,
    private val keyCipher: KeyCipher,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun store(
        accountId: UUID,
        secret: ApiKeySecret,
    ): ApiKeyResponse {
        apiKeyRepository.lockAccount(accountId)
        // Under the lock the disable listener also takes: disabled while the key was checked is seen
        // here, and disabled after this is seen by the listener, which waits for this to commit.
        if (!accountService.isActive(accountId)) throw GlossException(CommonError.UNAUTHORIZED)
        val now = clock.instant()
        inUse(accountId)?.let { revoke(it, KeyRevocation.REPLACED, now) }
        // Flushed first, so the one in use is revoked before the unique index sees the new one.
        apiKeyRepository.flush()
        val sealed = keyCipher.seal(secret.value, accountId, PROVIDER.code)
        val apiKey =
            ApiKey.register(
                accountId = accountId,
                provider = PROVIDER,
                sealed = sealed,
                last4 = secret.last4,
                now = now,
            )
        apiKeyRepository.save(apiKey)
        publish(
            ApiKeySaved(eventId = UuidV7.at(now), occurredAt = now, accountId = accountId, provider = PROVIDER.code),
        )
        return ApiKeyResponse.from(apiKey)
    }

    @Transactional
    fun revoke(
        accountId: UUID,
        reason: KeyRevocation,
    ) {
        apiKeyRepository.lockAccount(accountId)
        // The account as it stands now, not when the event was raised: a save that read it active
        // before the disable committed has finished under this lock, and an account enabled again
        // before a late event keeps the key its reader holds.
        if (reason == KeyRevocation.ACCOUNT_DISABLED && accountService.isActive(accountId)) return
        val apiKey = inUse(accountId) ?: return
        revoke(apiKey, reason, clock.instant())
    }

    @Transactional
    fun access(accountId: UUID): KeyAccess {
        // Under the lock a save or revoke takes, so marking a key unreadable never races a replacement.
        apiKeyRepository.lockAccount(accountId)
        val apiKey = inUse(accountId) ?: return KeyAccess.Missing
        val sealed = apiKey.sealed
        val opened = sealed?.let { keyCipher.open(it, accountId, apiKey.provider.code) }
        return when {
            apiKey.status == ApiKeyStatus.INVALID -> {
                KeyAccess.Rejected
            }

            apiKey.status == ApiKeyStatus.UNREADABLE -> {
                KeyAccess.Unreadable
            }

            opened == null -> {
                apiKey.markUnreadable()
                KeyAccess.Unreadable
            }

            else -> {
                KeyAccess.Usable(keyId = apiKey.id, secret = opened)
            }
        }
    }

    @Transactional
    fun markRejected(
        accountId: UUID,
        keyId: UUID,
    ) {
        // Under the lock a save or revoke takes, so a key replaced meanwhile is not the one marked.
        apiKeyRepository.lockAccount(accountId)
        inUse(accountId)?.takeIf { it.id == keyId }?.markRejected()
    }

    @Transactional
    fun recordUse(keyId: UUID) {
        apiKeyRepository.recordUse(keyId, clock.instant())
    }

    @Transactional(readOnly = true)
    fun inUse(accountId: UUID): ApiKey? =
        apiKeyRepository.findByAccountIdAndProviderAndRevokedAtIsNull(accountId, PROVIDER)

    private fun revoke(
        apiKey: ApiKey,
        reason: KeyRevocation,
        now: Instant,
    ) {
        if (!apiKey.revoke(now)) return
        publish(
            ApiKeyRevoked(
                eventId = UuidV7.at(now),
                occurredAt = now,
                accountId = apiKey.accountId,
                provider = apiKey.provider.code,
                reason = reason,
            ),
        )
    }

    private fun publish(event: ApiKeyEvent) = applicationEventPublisher.publishEvent(event)

    private companion object {
        // The only provider until an OpenAI-compatible base URL is allowed (decision K1).
        val PROVIDER = KeyProvider.DEEPSEEK
    }
}
