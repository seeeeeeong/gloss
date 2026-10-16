package gloss.domain.account.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommonPasswordsTest {
    private val commonPasswords = CommonPasswords()

    @Test
    fun `a password from the breach list is common, whatever its case`() {
        assertThat(commonPasswords.contains("qwertyuiop")).isTrue()
        assertThat(commonPasswords.contains("QwertyUIOP")).isTrue()
    }

    @Test
    fun `a password outside the list is not`() {
        assertThat(commonPasswords.contains("a new and longer password")).isFalse()
    }
}
