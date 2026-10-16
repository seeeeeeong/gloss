package gloss.domain.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordTest {
    @Test
    fun `10 characters is enough`() {
        assertThat(Password.check("a".repeat(10))).isInstanceOf(PasswordDecision.Accepted::class.java)
    }

    @Test
    fun `9 characters is too short`() {
        assertThat(Password.check("a".repeat(9))).isEqualTo(PasswordDecision.TooShort)
    }

    @Test
    fun `length counts characters, not bytes`() {
        assertThat(Password.check("가".repeat(10))).isInstanceOf(PasswordDecision.Accepted::class.java)
    }

    @Test
    fun `more than 72 bytes is refused, because bcrypt ignores the rest`() {
        assertThat(Password.check("a".repeat(72))).isInstanceOf(PasswordDecision.Accepted::class.java)
        assertThat(Password.check("a".repeat(73))).isEqualTo(PasswordDecision.TooLong)
        assertThat(Password.check("가".repeat(25))).isEqualTo(PasswordDecision.TooLong)
    }

    @Test
    fun `only whitespace is refused, however long`() {
        assertThat(Password.check(" ".repeat(20))).isEqualTo(PasswordDecision.Blank)
    }

    @Test
    fun `an accepted password is the one given, unchanged`() {
        val decision = Password.check(" correct horse battery ")

        assertThat((decision as PasswordDecision.Accepted).password.value).isEqualTo(" correct horse battery ")
    }
}
