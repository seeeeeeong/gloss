package gloss.domain.book.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Searches answered lately, in memory only: the source's terms allow caching but not a database of
 * its books, so a conversation keeps the words and refs it was shown and asks here to show them again.
 */
@Component
class EditionCache(
    private val clock: Clock,
) {
    private class Entry(
        val at: Instant,
        val editions: List<Edition>,
    )

    private val lock = Any()

    // Oldest use first, so the one dropped when full is the one least likely to be shown again.
    private val entries = LinkedHashMap<String, Entry>(MAX_ENTRIES, LOAD_FACTOR, true)

    // A conversation reopened shows its lists at once, often of the same words: one ask answers them all.
    private val asking = HashMap<String, CompletableFuture<List<Edition>>>()

    /**
     * What [query] found lately, or what [load] finds now; a load under way is waited for, not
     * repeated. A miss is let through [admit] first, so a caller refused there shares nothing.
     */
    fun get(
        query: String,
        admit: () -> Unit,
        load: () -> List<Edition>,
    ): List<Edition> =
        synchronized(lock) { fresh(query) } ?: run {
            admit()
            join(query, load)
        }

    private fun join(
        query: String,
        load: () -> List<Edition>,
    ): List<Edition> {
        val own = CompletableFuture<List<Edition>>()
        val waiting =
            synchronized(lock) {
                fresh(query)?.let { return it }
                asking.getOrPut(query) { own }
            }
        return if (waiting === own) ask(query, own, load) else wait(waiting)
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun ask(
        query: String,
        own: CompletableFuture<List<Edition>>,
        load: () -> List<Edition>,
    ): List<Edition> {
        val editions =
            try {
                load()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException,
            ) {
                synchronized(lock) { asking.remove(query) }
                own.completeExceptionally(e)
                throw e
            }
        synchronized(lock) {
            entries[query] = Entry(clock.instant(), editions)
            if (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
            asking.remove(query)
        }
        own.complete(editions)
        return editions
    }

    // A failed ask fails its waiters the same way, as the source's refusal would have.
    private fun wait(waiting: CompletableFuture<List<Edition>>): List<Edition> =
        try {
            waiting.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }

    private fun fresh(query: String): List<Edition>? {
        val entry = entries[query] ?: return null
        if (entry.at.plus(TTL).isAfter(clock.instant())) return entry.editions
        entries.remove(query)
        return null
    }

    private companion object {
        // Long enough to look a list over and come back to it, short enough to stay a cache.
        val TTL: Duration = Duration.ofMinutes(10)

        // A few invited readers' recent searches, each ten editions without their contents.
        const val MAX_ENTRIES = 500
        const val LOAD_FACTOR = 0.75f
    }
}
