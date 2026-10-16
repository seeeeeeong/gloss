package gloss.domain.book

import gloss.domain.book.domain.BookSource
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.BookSourceGateway
import gloss.domain.book.domain.Isbn13
import gloss.domain.book.domain.SourcedBook
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Holds the books a test puts in it instead of calling YES24, and remembers what it was asked. */
class FakeBookSourceGateway : BookSourceGateway {
    private val books = mutableListOf<SourcedBook>()
    var failing: BookSourceFailed.Kind? = null

    // Two requests at once ask it from two threads.
    val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun holds(
        ref: String,
        isbn13: String = "9788966262472",
        contents: String? = "1장 객체, 설계\n1.1 티켓 판매\n2장 객체지향",
        title: String = "오브젝트",
    ) {
        books +=
            SourcedBook(
                source = BookSource.YES24,
                ref = ref,
                url = "https://www.yes24.com/product/goods/$ref",
                title = title,
                authors = listOf("조영호 저"),
                publisher = "위키북스",
                isbn13 = checkNotNull(Isbn13.of(isbn13)),
                publishedOn = LocalDate.of(2019, 6, 17),
                kind = "도서",
                cover = "https://image.yes24.com/goods/$ref/L",
                contents = contents,
            )
    }

    private var gate: CountDownLatch? = null

    /** Counted down when a call reaches the source, as one held by [holdCalls] does. */
    var calling = CountDownLatch(1)
        private set

    /** Whether a transaction was open as each call reached the source, in order. */
    val inTransaction: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())

    override val source = BookSource.YES24

    override fun page(ref: String) = "https://www.yes24.com/product/goods/$ref"

    /** Calls wait at [gate] until it opens, so a test can act while one is under way. */
    fun holdCalls(gate: CountDownLatch) {
        calling = CountDownLatch(1)
        this.gate = gate
    }

    private fun reach() {
        inTransaction += TransactionSynchronizationManager.isActualTransactionActive()
        gate?.let {
            calling.countDown()
            it.await(GATE_SECONDS, TimeUnit.SECONDS)
        }
    }

    /** Run as each search reaches the source, as a test that moves the clock meanwhile. */
    var whenAsked: () -> Unit = {}

    override fun search(query: String): List<SourcedBook> {
        asked += query
        whenAsked()
        reach()
        failing?.let { throw BookSourceFailed(it) }
        return books.toList()
    }

    override fun find(ref: String): SourcedBook? {
        asked += ref
        reach()
        failing?.let { throw BookSourceFailed(it) }
        return books.firstOrNull { it.ref == ref }
    }

    fun reset() {
        whenAsked = {}
        gate = null
        inTransaction.clear()
        books.clear()
        failing = null
        asked.clear()
    }

    private companion object {
        const val GATE_SECONDS = 10L
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakeBookSourceGateway() = FakeBookSourceGateway()
    }
}
