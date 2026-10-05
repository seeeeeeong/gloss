package gloss.domain.account.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordPolicyTest {
    @Test
    fun `10 characters is enough`() {
        assertThat(PasswordPolicy.check("a".repeat(10))).isEqualTo(PasswordDecision.Accepted)
    }

    @Test
    fun `9 characters is too short`() {
        assertThat(PasswordPolicy.check("a".repeat(9))).isEqualTo(PasswordDecision.TooShort)
    }

    @Test
    fun `length counts characters, not bytes`() {
        assertThat(PasswordPolicy.check("가".repeat(10))).isEqualTo(PasswordDecision.Accepted)
    }

    @Test
    fun `more than 72 bytes is refused, because bcrypt ignores the rest`() {
        assertThat(PasswordPolicy.check("a".repeat(72))).isEqualTo(PasswordDecision.Accepted)
        assertThat(PasswordPolicy.check("a".repeat(73))).isEqualTo(PasswordDecision.TooLong)
        assertThat(PasswordPolicy.check("가".repeat(25))).isEqualTo(PasswordDecision.TooLong)
    }

    @Test
    fun `only whitespace is refused, however long`() {
        assertThat(PasswordPolicy.check(" ".repeat(20))).isEqualTo(PasswordDecision.Blank)
    }
}
