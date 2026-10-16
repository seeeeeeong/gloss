package gloss.domain.apikey.service

import gloss.common.error.GlossException
import gloss.domain.apikey.domain.ApiKeySecret
import gloss.domain.apikey.domain.KeyCheckGateway
import gloss.domain.apikey.domain.KeyCheckResult
import gloss.domain.apikey.domain.event.KeyRevocation
import gloss.domain.apikey.dto.request.ApiKeySaveRequest
import gloss.domain.apikey.dto.response.ApiKeyResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ApiKeyService(
    private val keyCheckGateway: KeyCheckGateway,
    private val apiKeyStore: ApiKeyStore,
) {
    fun current(accountId: UUID): ApiKeyResponse =
        apiKeyStore.inUse(accountId)?.let(ApiKeyResponse::from) ?: throw GlossException(ApiKeyError.NOT_REGISTERED)

    /** Checked with the provider first, outside any transaction, since that is a network call. */
    fun save(
        accountId: UUID,
        request: ApiKeySaveRequest,
    ): ApiKeyResponse {
        val secret = ApiKeySecret.of(request.key) ?: throw GlossException(ApiKeyError.INVALID_KEY)
        val error =
            when (keyCheckGateway.check(secret)) {
                KeyCheckResult.Valid -> null
                KeyCheckResult.Rejected -> ApiKeyError.KEY_REJECTED
                KeyCheckResult.Unavailable -> ApiKeyError.KEY_CHECK_UNAVAILABLE
            }
        if (error != null) throw GlossException(error)
        return apiKeyStore.store(accountId, secret)
    }

    /** For a call on the reader's key: open it, or say why it cannot be used. */
    fun access(accountId: UUID): KeyAccess = apiKeyStore.access(accountId)

    /** The provider answered 401: the key stops being used until the reader saves another. */
    fun markRejected(
        accountId: UUID,
        keyId: UUID,
    ) {
        apiKeyStore.markRejected(accountId, keyId)
    }

    fun recordUse(keyId: UUID) {
        apiKeyStore.recordUse(keyId)
    }

    /** The reader removing their own key. */
    fun revoke(accountId: UUID) {
        apiKeyStore.revoke(accountId, KeyRevocation.READER)
    }

    fun revokeForDisabledAccount(accountId: UUID) {
        apiKeyStore.revoke(accountId, KeyRevocation.ACCOUNT_DISABLED)
    }
}
