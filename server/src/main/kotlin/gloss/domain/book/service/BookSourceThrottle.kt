package gloss.domain.book.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * How often the book source may be asked. The operator's one key serves every reader, so one reader
 * is held to a pace of their own, and all of them together to less than the key allows a second and
 * a day. Counted in memory under one lock: one instance runs, asks are a few a second at most, and
 * a restart that forgets the day's count is caught by the source's own refusal.
 */
@Component
class BookSourceThrottle(
    @Value("\${gloss.book-source.per-minute}") private val perMinute: Int,
    @Value("\${gloss.book-source.per-second}") private val perSecond: Int,
    @Value("\${gloss.book-source.per-day}") private val perDay: Int,
    private val clock: Clock,
) {
    enum class Verdict {
        ALLOWED,

        /** This reader has asked as often as a minute allows. */
        READER_TOO_OFTEN,

        /** Every reader together has used what the key allows for this second or this day. */
        KEY_SPENT,
    }

    private val lock = Any()
    private val byReader = HashMap<UUID, ArrayDeque<Instant>>()
    private val lastSecond = ArrayDeque<Instant>()
    private var day: LocalDate? = null
    private var askedToday = 0

    /**
     * A reader's own ask that reaches the source: their pace and the key's together. An allowed ask
     * is counted; a refused one is not.
     */
    fun ask(accountId: UUID): Verdict =
        synchronized(lock) {
            val now = clock.instant()
            forget(now)
            val verdict = verdictOf(readerFull(accountId), keyFull())
            if (verdict == Verdict.ALLOWED) {
                countReader(accountId, now)
                countKey(now)
            }
            verdict
        }

    /**
     * A reader's search that may be answered by another's ask of the same words: it counts toward
     * their own pace, but not the key's, which only the ask that reaches the source spends.
     */
    fun reader(accountId: UUID): Verdict =
        synchronized(lock) {
            val now = clock.instant()
            forget(now)
            val verdict = verdictOf(readerFull(accountId), keyFull = false)
            if (verdict == Verdict.ALLOWED) countReader(accountId, now)
            verdict
        }

    /** The one ask that reaches the source for searches waiting on it: the key's pace only. */
    fun source(): Verdict =
        synchronized(lock) {
            val now = clock.instant()
            forget(now)
            val verdict = verdictOf(readerFull = false, keyFull = keyFull())
            if (verdict == Verdict.ALLOWED) countKey(now)
            verdict
        }

    private fun readerFull(accountId: UUID) = (byReader[accountId]?.size ?: 0) >= perMinute

    private fun keyFull() = lastSecond.size >= perSecond || askedToday >= perDay

    private fun verdictOf(
        readerFull: Boolean,
        keyFull: Boolean,
    ): Verdict =
        when {
            readerFull -> Verdict.READER_TOO_OFTEN
            keyFull -> Verdict.KEY_SPENT
            else -> Verdict.ALLOWED
        }

    private fun countReader(
        accountId: UUID,
        now: Instant,
    ) {
        byReader.getOrPut(accountId, ::ArrayDeque).addLast(now)
    }

    private fun countKey(now: Instant) {
        lastSecond.addLast(now)
        askedToday++
    }

    // Every reader's old asks go at each ask, so the map holds only readers who asked in the last minute.
    private fun forget(now: Instant) {
        byReader.values.forEach { it.dropUntil(now.minus(MINUTE)) }
        byReader.values.removeIf { it.isEmpty() }
        lastSecond.dropUntil(now.minus(SECOND))
        val today = LocalDate.ofInstant(now, SOURCE_ZONE)
        if (today != day) {
            day = today
            askedToday = 0
        }
    }

    private fun ArrayDeque<Instant>.dropUntil(cutoff: Instant) {
        while (isNotEmpty() && !first().isAfter(cutoff)) removeFirst()
    }

    private companion object {
        val MINUTE: Duration = Duration.ofMinutes(1)
        val SECOND: Duration = Duration.ofSeconds(1)

        // YES24 starts a key's day over at midnight in Korea.
        val SOURCE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
