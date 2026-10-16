package gloss.domain.book.service

import gloss.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EditionCacheTest {
    private val clock = MutableClock()
    private val cache = EditionCache(clock)
    private val edition =
        Edition(
            ref = "101",
            source = "yes24",
            url = "https://www.yes24.com/product/goods/101",
            title = "오브젝트",
            authors = emptyList(),
            publisher = null,
            isbn13 = "9788966262472",
            publishedOn = null,
            kind = null,
            cover = null,
            hasContents = true,
        )

    @Test
    fun `a search is answered from memory for ten minutes, then asked again`() {
        cache.get("오브젝트", admit = {}) { listOf(edition) }

        clock.advance(Duration.ofMinutes(9))
        assertThat(cache.get("오브젝트", admit = {}) { emptyList() }).containsExactly(edition)
        clock.advance(Duration.ofMinutes(1))
        assertThat(cache.get("오브젝트", admit = {}) { emptyList() }).isEmpty()
    }

    @Test
    fun `when full, the search used least lately is dropped`() {
        repeat(501) { cache.get("q$it", admit = {}) { listOf(edition) } }

        assertThat(cache.get("q0", admit = {}) { emptyList() }).isEmpty()
        assertThat(cache.get("q500", admit = {}) { emptyList() }).containsExactly(edition)
    }

    @Test
    fun `the same words asked at once are asked of the source once, and all get its answer`() {
        val calls = AtomicInteger()
        val gate = CountDownLatch(1)
        val arrived = CountDownLatch(4)
        val pool = Executors.newFixedThreadPool(4)
        val answers =
            List(4) {
                pool.submit<List<Edition>> {
                    arrived.countDown()
                    cache.get("오브젝트", admit = {}) {
                        calls.incrementAndGet()
                        gate.await(5, TimeUnit.SECONDS)
                        listOf(edition)
                    }
                }
            }
        // A thread that arrives after the answer finds it remembered, so one ask is all there can be.
        arrived.await(5, TimeUnit.SECONDS)
        gate.countDown()

        assertThat(answers.map { it.get(5, TimeUnit.SECONDS) }).containsOnly(listOf(edition))
        assertThat(calls.get()).isEqualTo(1)
        pool.shutdown()
    }

    @Test
    fun `a failed ask fails its waiters too, and the next ask tries again`() {
        val failure = IllegalStateException("source down")

        assertThatThrownBy { cache.get("오브젝트", admit = {}) { throw failure } }.isSameAs(failure)
        assertThat(cache.get("오브젝트", admit = {}) { listOf(edition) }).containsExactly(edition)
    }

    @Test
    fun `a caller refused at the door shares nothing, and leaves the ask to others`() {
        val refused = IllegalStateException("too often")

        assertThatThrownBy { cache.get("오브젝트", admit = { throw refused }) { listOf(edition) } }.isSameAs(refused)
        assertThat(cache.get("오브젝트", admit = {}) { listOf(edition) }).containsExactly(edition)
    }
}
