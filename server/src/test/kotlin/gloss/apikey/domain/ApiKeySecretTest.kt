package gloss.apikey.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiKeySecretTest {
    @Test
    fun `a key is trimmed, and its last four characters are what is shown`() {
        val secret = ApiKeySecret.of("  sk-0123456789abcdef  ")

        assertThat(secret?.value).isEqualTo("sk-0123456789abcdef")
        assertThat(secret?.last4).isEqualTo("cdef")
    }

    @Test
    fun `a blank key, one with spaces inside or a control character is refused`() {
        assertThat(ApiKeySecret.of("   ")).isNull()
        assertThat(ApiKeySecret.of("sk-0123 456789abcdef")).isNull()
        assertThat(ApiKeySecret.of("sk-0123456789abcdef\u0000")).isNull()
        assertThat(ApiKeySecret.of("sk-키0123456789abcdef")).isNull()
    }

    @Test
    fun `a key too short to be one or too long to be sent is refused`() {
        assertThat(ApiKeySecret.of("sk-123")).isNull()
        assertThat(ApiKeySecret.of("sk-" + "a".repeat(300))).isNull()
    }

    @Test
    fun `printing it shows only the last four characters`() {
        assertThat(ApiKeySecret.of("sk-0123456789abcdef").toString()).doesNotContain("0123456789ab").contains("cdef")
    }
}
