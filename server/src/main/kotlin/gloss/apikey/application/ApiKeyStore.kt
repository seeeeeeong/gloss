package gloss.apikey.application

import gloss.account.application.AccountService
import gloss.apikey.application.dto.ApiKeyResponse
import gloss.apikey.domain.ApiKey
import gloss.apikey.domain.ApiKeyRepository
import gloss.apikey.domain.ApiKeySecret
import gloss.apikey.domain.KeyCipher
import gloss.apikey.domain.KeyProvider
import gloss.apikey.domain.event.ApiKeyEvent
import gloss.apikey.domain.event.ApiKeyRevoked
import gloss.apikey.domain.event.ApiKeySaved
import gloss.apikey.domain.event.KeyRevocation
import gloss.common.error.CommonError
import gloss.common.error.GlossException
import gloss.common.util.UuidV7
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
