package gloss.domain.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TemporaryPasswordPolicyTest {
    @Test
    fun `a temporary password is twenty characters the password policy accepts`() {
        val password = TemporaryPasswordPolicy.generate()

        assertThat(password).hasSize(20)
        assertThat(Password.check(password)).isInstanceOf(PasswordDecision.Accepted::class.java)
    }

    @Test
    fun `it leaves out characters easy to misread when read aloud or copied by hand`() {
        val passwords = (1..200).joinToString("") { TemporaryPasswordPolicy.generate() }

        assertThat(passwords).doesNotContain("0", "O", "1", "l", "I")
    }

    @Test
    fun `two are never the same`() {
        assertThat(TemporaryPasswordPolicy.generate()).isNotEqualTo(TemporaryPasswordPolicy.generate())
    }
}
