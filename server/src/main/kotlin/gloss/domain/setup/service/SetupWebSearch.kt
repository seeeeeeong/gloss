package gloss.domain.setup.service

import gloss.domain.setup.domain.WebResults
import gloss.domain.setup.domain.WebSearchFailed
import gloss.domain.setup.domain.WebSearchGateway
import gloss.domain.setup.domain.WebSearchPolicy
import gloss.domain.setup.infrastructure.JdbcWebSearches
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * A web search on the operator's key, within its daily caps, recorded with what it cost. Asked outside
 * any transaction: the cap is taken and released before the engine is called.
 */
@Service
class SetupWebSearch(
    private val webSearchGateway: WebSearchGateway,
    private val jdbcWebSearches: JdbcWebSearches,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
    @Value("\${gloss.setup.web-searches-per-reader}") perReader: Int,
    @Value("\${gloss.setup.web-searches-per-day}") perDay: Int,
) {
    private val caps = WebSearchPolicy.Caps(perReader = perReader, perDay = perDay)

    /** Without the operator's key the model is not offered a web search at all. */
    val available: Boolean get() = webSearchGateway.available

    sealed interface Searched {
        data class Found(
            val results: WebResults,
        ) : Searched

        /** [problem] is what the model is told. */
        data class Refused(
            val problem: String,
        ) : Searched
    }

    fun search(
        turn: SetupTurn,
        query: String,
    ): Searched {
        val asked =
            checkNotNull(
                transactionTemplate.execute {
                    jdbcWebSearches.ask(
                        accountId = turn.accountId,
                        sessionId = turn.sessionId,
                        caps = caps,
                        now = clock.instant(),
                    )
                },
            )
        val id =
            when (asked) {
                is JdbcWebSearches.Asked.Allowed -> asked.id

                is JdbcWebSearches.Asked.Refused -> return Searched.Refused(
                    "web search is used up for today; use find_books",
                )
            }
        val results =
            try {
                webSearchGateway.search(query)
            } catch (_: WebSearchFailed) {
                jdbcWebSearches.answer(id, answer = null, at = clock.instant())
                return Searched.Refused("the web search failed; use find_books")
            }
        val answer =
            JdbcWebSearches.Answer(
                results = results.pages.size,
                costMicros = results.costMicros,
                requestId = results.requestId,
            )
        jdbcWebSearches.answer(id, answer, clock.instant())
        return Searched.Found(results)
    }
}
