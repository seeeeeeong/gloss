package gloss.domain.account.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginIdPolicyTest {
    @Test
    fun `a login id is trimmed and lowercased`() {
        assertThat(LoginIdPolicy.normalize("  Reader.One ")).isEqualTo("reader.one")
    }

    @Test
    fun `letters, digits, dots, dashes and underscores are allowed`() {
        assertThat(LoginIdPolicy.normalize("a_b-c.9")).isEqualTo("a_b-c.9")
    }

    @Test
    fun `a login id is 3 to 32 characters`() {
        assertThat(LoginIdPolicy.normalize("ab")).isNull()
        assertThat(LoginIdPolicy.normalize("a".repeat(32))).isNotNull()
        assertThat(LoginIdPolicy.normalize("a".repeat(33))).isNull()
    }

    @Test
    fun `it starts with a letter or digit`() {
        assertThat(LoginIdPolicy.normalize(".reader")).isNull()
    }

    @Test
    fun `hangul, spaces and at signs are refused`() {
        assertThat(LoginIdPolicy.normalize("독자")).isNull()
        assertThat(LoginIdPolicy.normalize("a reader")).isNull()
        assertThat(LoginIdPolicy.normalize("reader@example.com")).isNull()
    }
}
