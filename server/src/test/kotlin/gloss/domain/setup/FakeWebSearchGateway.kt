package gloss.domain.setup

import gloss.domain.setup.domain.WebPage
import gloss.domain.setup.domain.WebResults
import gloss.domain.setup.domain.WebSearchFailed
import gloss.domain.setup.domain.WebSearchGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.Collections

/** Answers with the pages a test puts in it instead of calling Exa, and remembers what it was asked. */
class FakeWebSearchGateway : WebSearchGateway {
    override var available = true
    var pages: List<WebPage> = emptyList()
    var failing: WebSearchFailed.Kind? = null
    val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun search(query: String): WebResults {
        asked += query
        failing?.let { throw WebSearchFailed(it) }
        return WebResults(requestId = "exa-request", costMicros = COST_MICROS, pages = pages)
    }

    fun reset() {
        available = true
        pages = emptyList()
        failing = null
        asked.clear()
    }

    companion object {
        // What Exa reports for an auto search of up to ten results: $7 per thousand.
        const val COST_MICROS = 7_000L
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakeWebSearchGateway() = FakeWebSearchGateway()
    }
}
