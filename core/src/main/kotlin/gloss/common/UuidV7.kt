package gloss.common

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/** RFC 9562. The JDK has no v7, and v4 ids scatter inserts across the index. */
object UuidV7 {
    private val random = SecureRandom()

    fun at(instant: Instant): UUID {
        val millis = instant.toEpochMilli()
        val high = (millis shl TIMESTAMP_SHIFT) or VERSION or (random.nextLong() and RAND_A_MASK)
        val low = (random.nextLong() and RAND_B_MASK) or VARIANT
        return UUID(high, low)
    }

    private const val TIMESTAMP_SHIFT = 16
    private const val VERSION = 0x7000L
    private const val RAND_A_MASK = 0x0FFFL
    private const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    private const val VARIANT = Long.MIN_VALUE
}
