package gloss.domain.setup

import gloss.MutableClock
import gloss.domain.book.FakeBookSourceGateway
import gloss.domain.book.service.EditionCache
import gloss.domain.llm.FakeLlmGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/** Everything a model turn reaches outside Gloss, played by fakes, and the clock it runs on. */
class SetupFakes(
    val books: FakeBookSourceGateway,
    val web: FakeWebSearchGateway,
    val model: FakeLlmGateway,
    val clock: MutableClock,
    private val editionCache: EditionCache,
) {
    /** Each test starts with none of the last one's books, pages, replies, searches or time. */
    fun reset() {
        books.reset()
        web.reset()
        model.reset()
        clock.reset()
        editionCache.clear()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun setupFakes(
            fakeBookSourceGateway: FakeBookSourceGateway,
            fakeWebSearchGateway: FakeWebSearchGateway,
            fakeLlmGateway: FakeLlmGateway,
            mutableClock: MutableClock,
            editionCache: EditionCache,
        ) = SetupFakes(
            books = fakeBookSourceGateway,
            web = fakeWebSearchGateway,
            model = fakeLlmGateway,
            clock = mutableClock,
            editionCache = editionCache,
        )
    }
}
