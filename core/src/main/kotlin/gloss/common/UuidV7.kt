package gloss.common

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * UUIDv7 (RFC 9562): a millisecond timestamp in the top 48 bits, then random bits. Ids
 * made later sort later, so an id orders like its creation time and an index
 * on it stays append-mostly. The JDK makes only random (v4) and name-based (v3) UUIDs.
 */
object UuidV7 {
    private val random = SecureRandom()

    fun at(instant: Instant): UUID {
        val millis = instant.toEpochMilli()
        val high = (millis shl TIMESTAMP_SHIFT) or VERSION or (random.nextLong() and RAND_A_MASK)
        val low = (random.nextLong() and RAND_B_MASK) or VARIANT
        return UUID(high, low)
    }

    /** The timestamp takes the top 48 of the 64 high bits. */
    private const val TIMESTAMP_SHIFT = 16

    /** Version 7, in bits 12 to 15 of the high half. */
    private const val VERSION = 0x7000L

    /** The 12 random bits after the version. */
    private const val RAND_A_MASK = 0x0FFFL

    /** The 62 random bits after the variant. */
    private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL

    /** The RFC 4122 variant, `10` in the top two bits of the low half. */
    private const val VARIANT = Long.MIN_VALUE
}
