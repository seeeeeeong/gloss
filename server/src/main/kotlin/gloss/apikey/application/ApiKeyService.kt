package gloss.apikey.application

import gloss.apikey.application.dto.ApiKeyResponse
import gloss.apikey.application.dto.ApiKeySaveRequest
import gloss.apikey.domain.ApiKeySecret
import gloss.apikey.domain.KeyCheckGateway
import gloss.apikey.domain.KeyCheckResult
import gloss.apikey.domain.event.KeyRevocation
import gloss.common.error.GlossException
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

    /** The reader removing their own key. */
    fun revoke(accountId: UUID) {
        apiKeyStore.revoke(accountId, KeyRevocation.READER)
    }

    fun revokeForDisabledAccount(accountId: UUID) {
        apiKeyStore.revoke(accountId, KeyRevocation.ACCOUNT_DISABLED)
    }
}
