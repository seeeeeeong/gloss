package gloss.domain.setup

import gloss.domain.setup.domain.FetchedPage
import gloss.domain.setup.domain.PageFetchFailed
import gloss.domain.setup.domain.PageGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.Collections

/** Answers with the pages a test puts in it instead of reading the web, and remembers what it was asked. */
class FakePageGateway : PageGateway {
    val pages: MutableMap<String, FetchedPage> = Collections.synchronizedMap(mutableMapOf())
    val failing: MutableMap<String, PageFetchFailed.Kind> = Collections.synchronizedMap(mutableMapOf())
    val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Run as each page is read, as a test that moves the clock meanwhile. */
    var whenFetched: () -> Unit = {}

    override fun fetch(url: String): FetchedPage {
        asked += url
        whenFetched()
        failing[url]?.let { throw PageFetchFailed(it) }
        return pages[url] ?: throw PageFetchFailed(PageFetchFailed.Kind.FAILED)
    }

    fun reset() {
        whenFetched = {}
        pages.clear()
        failing.clear()
        asked.clear()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakePageGateway() = FakePageGateway()
    }
}
