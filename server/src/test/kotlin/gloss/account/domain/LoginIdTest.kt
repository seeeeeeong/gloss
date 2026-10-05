package gloss.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginIdTest {
    @Test
    fun `a login id is trimmed and lowercased`() {
        assertThat(LoginId.of("  Reader.One ")?.value).isEqualTo("reader.one")
    }

    @Test
    fun `letters, digits, dots, dashes and underscores are allowed`() {
        assertThat(LoginId.of("a_b-c.9")?.value).isEqualTo("a_b-c.9")
    }

    @Test
    fun `a login id is 3 to 32 characters`() {
        assertThat(LoginId.of("ab")?.value).isNull()
        assertThat(LoginId.of("a".repeat(32))?.value).isNotNull()
        assertThat(LoginId.of("a".repeat(33))?.value).isNull()
    }

    @Test
    fun `it starts with a letter or digit`() {
        assertThat(LoginId.of(".reader")?.value).isNull()
    }

    @Test
    fun `hangul, spaces and at signs are refused`() {
        assertThat(LoginId.of("독자")?.value).isNull()
        assertThat(LoginId.of("a reader")?.value).isNull()
        assertThat(LoginId.of("reader@example.com")?.value).isNull()
    }
}
