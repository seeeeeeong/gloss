package gloss.apikey.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class KeyCipherTest {
    private val v1 = ByteArray(32) { 1 }
    private val v2 = ByteArray(32) { 2 }
    private val accountId = UUID.randomUUID()
    private val cipher = KeyCipher(mapOf(1 to v1))

    @Test
    fun `a sealed key opens again for the same account and provider`() {
        val sealed = cipher.seal("sk-reader-key", accountId, "deepseek")

        assertThat(sealed.ciphertext).isNotEqualTo("sk-reader-key".toByteArray())
        assertThat(sealed.nonce).hasSize(12)
        assertThat(cipher.open(sealed, accountId, "deepseek")).isEqualTo("sk-reader-key")
    }

    @Test
    fun `each seal uses a fresh nonce`() {
        val first = cipher.seal("sk-reader-key", accountId, "deepseek")
        val second = cipher.seal("sk-reader-key", accountId, "deepseek")

        assertThat(first.nonce).isNotEqualTo(second.nonce)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun `a sealed key moved to another account does not open`() {
        val sealed = cipher.seal("sk-reader-key", accountId, "deepseek")

        assertThat(cipher.open(sealed, UUID.randomUUID(), "deepseek")).isNull()
    }

    @Test
    fun `a changed byte does not open`() {
        val sealed = cipher.seal("sk-reader-key", accountId, "deepseek")
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertThat(cipher.open(KeyCipher.Sealed(tampered, sealed.nonce, sealed.keyVersion), accountId, "deepseek"))
            .isNull()
    }

    @Test
    fun `the newest version seals, and an older one still opens what it sealed`() {
        val old = cipher.seal("sk-reader-key", accountId, "deepseek")
        val rotated = KeyCipher(mapOf(1 to v1, 2 to v2))

        assertThat(rotated.seal("sk-reader-key", accountId, "deepseek").keyVersion).isEqualTo(2)
        assertThat(rotated.open(old, accountId, "deepseek")).isEqualTo("sk-reader-key")
    }

    @Test
    fun `a version no longer configured does not open`() {
        val sealed = KeyCipher(mapOf(2 to v2)).seal("sk-reader-key", accountId, "deepseek")

        assertThat(cipher.open(sealed, accountId, "deepseek")).isNull()
    }
}
