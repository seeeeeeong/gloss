package gloss.domain.apikey.domain

import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a fresh nonce per key. The account, provider and key version are bound in as
 * associated data, so ciphertext copied to another row does not open. The newest version seals;
 * older ones only open what they sealed.
 */
class KeyCipher(
    private val keys: Map<Int, ByteArray>,
) {
    private val random = SecureRandom()
    private val activeVersion = keys.keys.max()

    fun seal(
        plaintext: String,
        accountId: UUID,
        provider: String,
    ): Sealed {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, keys.getValue(activeVersion), nonce)
        cipher.updateAAD(aad(accountId, provider, activeVersion))
        return Sealed(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), nonce, activeVersion)
    }

    /** Null when it does not open: another row's ciphertext, a changed byte, or a retired version. */
    fun open(
        sealed: Sealed,
        accountId: UUID,
        provider: String,
    ): String? {
        val key = keys[sealed.keyVersion] ?: return null
        val cipher = cipher(Cipher.DECRYPT_MODE, key, sealed.nonce)
        cipher.updateAAD(aad(accountId, provider, sealed.keyVersion))
        return try {
            String(cipher.doFinal(sealed.ciphertext), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private fun cipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
    ): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }

    private fun aad(
        accountId: UUID,
        provider: String,
        version: Int,
    ) = "$accountId|$provider|$version".toByteArray(Charsets.UTF_8)

    class Sealed(
        val ciphertext: ByteArray,
        val nonce: ByteArray,
        val keyVersion: Int,
    )

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        // GCM's recommended nonce; random per seal, so one key may seal about 2^32 times before a repeat is likely.
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
