package gloss.domain.apikey.domain

import gloss.common.entity.BaseEntity
import gloss.common.util.UuidV7
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicUpdate
import java.time.Instant
import java.util.UUID

@Entity
@DynamicUpdate
class ApiKey private constructor(
    id: UUID,
    accountId: UUID,
    provider: KeyProvider,
    sealed: KeyCipher.Sealed,
    last4: String,
    now: Instant,
) : BaseEntity(id) {
    var accountId: UUID = accountId
        protected set

    @Convert(converter = KeyProvider.Column::class)
    var provider: KeyProvider = provider
        protected set
    var ciphertext: ByteArray? = sealed.ciphertext
        protected set
    var nonce: ByteArray? = sealed.nonce
        protected set
    var keyVersion: Int = sealed.keyVersion
        protected set
    var last4: String = last4
        protected set

    @Convert(converter = ApiKeyStatus.Column::class)
    var status: ApiKeyStatus = ApiKeyStatus.VALID
        protected set
    var validatedAt: Instant? = now
        protected set
    var lastUsedAt: Instant? = null
        protected set
    var lastError: String? = null
        protected set
    var createdAt: Instant = now
        protected set
    var revokedAt: Instant? = null
        protected set

    val sealed: KeyCipher.Sealed?
        get() {
            val bytes = ciphertext ?: return null
            return KeyCipher.Sealed(bytes, checkNotNull(nonce), keyVersion)
        }

    fun markUnreadable() {
        status = ApiKeyStatus.UNREADABLE
        lastError = "does not open with the configured master keys"
    }

    fun markRejected() {
        status = ApiKeyStatus.INVALID
        lastError = "refused by the provider"
    }

    /** False when already revoked. The ciphertext goes, so a key the reader removed never opens again. */
    fun revoke(at: Instant): Boolean {
        if (revokedAt != null) return false
        status = ApiKeyStatus.REVOKED
        revokedAt = at
        ciphertext = null
        nonce = null
        return true
    }

    companion object {
        /** Made after the provider accepted the key, so it starts valid. */
        fun register(
            accountId: UUID,
            provider: KeyProvider,
            sealed: KeyCipher.Sealed,
            last4: String,
            now: Instant,
        ) = ApiKey(
            id = UuidV7.at(now),
            accountId = accountId,
            provider = provider,
            sealed = sealed,
            last4 = last4,
            now = now,
        )
    }
}

enum class ApiKeyStatus(
    val code: String,
) {
    VALID("valid"),
    INVALID("invalid"),
    UNREADABLE("unreadable"),
    REVOKED("revoked"),
    ;

    @Converter
    class Column : AttributeConverter<ApiKeyStatus, String> {
        override fun convertToDatabaseColumn(status: ApiKeyStatus): String = status.code

        override fun convertToEntityAttribute(code: String): ApiKeyStatus = entries.first { it.code == code }
    }
}

enum class KeyProvider(
    val code: String,
) {
    DEEPSEEK("deepseek"),
    ;

    @Converter
    class Column : AttributeConverter<KeyProvider, String> {
        override fun convertToDatabaseColumn(provider: KeyProvider): String = provider.code

        override fun convertToEntityAttribute(code: String): KeyProvider = entries.first { it.code == code }
    }
}
