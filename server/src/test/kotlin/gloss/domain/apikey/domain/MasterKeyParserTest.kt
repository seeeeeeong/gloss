package gloss.domain.apikey.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class MasterKeyParserTest {
    private fun key(fill: Int) = Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })

    @Test
    fun `each version names a 32-byte key`() {
        val keys = MasterKeyParser.parse("1:${key(1)}, 2:${key(2)}")

        assertThat(keys.keys).containsExactlyInAnyOrder(1, 2)
        assertThat(keys.getValue(2)).hasSize(32).containsOnly(2)
    }

    @Test
    fun `nothing configured is refused, so keys are never stored without encryption`() {
        assertThatThrownBy { MasterKeyParser.parse(" ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a key of the wrong length or a malformed entry is refused`() {
        val short = Base64.getEncoder().encodeToString(ByteArray(16))

        assertThatThrownBy { MasterKeyParser.parse("1:$short") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { MasterKeyParser.parse(key(1)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { MasterKeyParser.parse("0:${key(1)}") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a version given twice is refused`() {
        assertThatThrownBy { MasterKeyParser.parse("1:${key(1)},1:${key(2)}") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
