package gloss.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class UuidV7Test {
    @Test
    fun `it is a version 7, RFC 4122 variant UUID`() {
        val id = UuidV7.at(Instant.parse("2026-09-25T10:00:00Z"))

        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }

    @Test
    fun `its top 48 bits are the millisecond it was made at`() {
        val at = Instant.parse("2026-09-25T10:00:00.123Z")

        assertThat(UuidV7.at(at).mostSignificantBits ushr 16).isEqualTo(at.toEpochMilli())
    }

    @Test
    fun `a later id sorts after an earlier one`() {
        val earlier = UuidV7.at(Instant.parse("2026-09-25T10:00:00Z"))
        val later = UuidV7.at(Instant.parse("2026-09-25T10:00:00.001Z"))

        // PostgreSQL compares uuids as unsigned bytes, which is the string order.
        assertThat(later.toString()).isGreaterThan(earlier.toString())
    }
}
