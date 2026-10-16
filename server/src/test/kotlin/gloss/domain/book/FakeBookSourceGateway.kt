package gloss.domain.book

import gloss.domain.book.domain.BookSource
import gloss.domain.book.domain.BookSourceFailed
import gloss.domain.book.domain.BookSourceGateway
import gloss.domain.book.domain.Isbn13
import gloss.domain.book.domain.SourcedBook
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.LocalDate

/** Holds the books a test puts in it instead of calling YES24, and remembers what it was asked. */
class FakeBookSourceGateway : BookSourceGateway {
    private val books = mutableListOf<SourcedBook>()
    var failing: BookSourceFailed.Kind? = null
    val asked = mutableListOf<String>()

    fun holds(
        ref: String,
        isbn13: String = "9788966262472",
        contents: String? = "1장 객체, 설계\n1.1 티켓 판매\n2장 객체지향",
    ) {
        books +=
            SourcedBook(
                source = BookSource.YES24,
                ref = ref,
                url = "https://www.yes24.com/product/goods/$ref",
                title = "오브젝트",
                authors = listOf("조영호 저"),
                publisher = "위키북스",
                isbn13 = checkNotNull(Isbn13.of(isbn13)),
                publishedOn = LocalDate.of(2019, 6, 17),
                kind = "도서",
                contents = contents,
            )
    }

    override fun search(query: String): List<SourcedBook> {
        asked += query
        failing?.let { throw BookSourceFailed(it) }
        return books.toList()
    }

    override fun find(ref: String): SourcedBook? {
        asked += ref
        failing?.let { throw BookSourceFailed(it) }
        return books.firstOrNull { it.ref == ref }
    }

    fun reset() {
        books.clear()
        failing = null
        asked.clear()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakeBookSourceGateway() = FakeBookSourceGateway()
    }
}
