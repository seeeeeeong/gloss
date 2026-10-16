package gloss.domain.setup.infrastructure

import gloss.common.util.UuidV7
import gloss.domain.setup.domain.WebSearchDecision
import gloss.domain.setup.domain.WebSearchPolicy
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** The web searches asked on the operator's key: counted against the caps, then answered with their cost. */
@Repository
class JdbcWebSearches(
    private val jdbcClient: JdbcClient,
) {
    /**
     * Records a search about to be asked when the caps allow it, in the caller's transaction. Counted
     * and written under one lock held to its commit, so searches asked at once never pass a cap together.
     */
    fun ask(
        accountId: UUID,
        sessionId: UUID,
        caps: WebSearchPolicy.Caps,
        now: Instant,
    ): Asked {
        jdbcClient
            .sql("select 1 from pg_advisory_xact_lock(hashtextextended('setup:web_search', 0))")
            .query(Int::class.java)
            .single()
        val since = Timestamp.from(now.minus(WebSearchPolicy.WINDOW))
        val all = count("select count(*) from web_search where asked_at > :since", since, null)
        val reader =
            count("select count(*) from web_search where account_id = :account and asked_at > :since", since, accountId)
        val decision = WebSearchPolicy.decide(readerAsked = reader, allAsked = all, caps = caps)
        if (decision != WebSearchDecision.ALLOWED) return Asked.Refused(decision)
        val id = UuidV7.at(now)
        jdbcClient
            .sql(
                """
                insert into web_search (id, account_id, session_id, asked_at)
                values (:id, :account, :session, :at)
                """.trimIndent(),
            ).param("id", id)
            .param("account", accountId)
            .param("session", sessionId)
            .param("at", Timestamp.from(now))
            .update()
        return Asked.Allowed(id)
    }

    sealed interface Asked {
        data class Allowed(
            val id: UUID,
        ) : Asked

        data class Refused(
            val decision: WebSearchDecision,
        ) : Asked
    }

    /** [answer] is null for a search that failed. Answered once: a second answer changes nothing. */
    fun answer(
        id: UUID,
        answer: Answer?,
        at: Instant,
    ) {
        jdbcClient
            .sql(
                """
                update web_search
                set answered_at = :at, outcome = :outcome, results = :results, cost_micros = :cost,
                    request_id = :request
                where id = :id and answered_at is null
                """.trimIndent(),
            ).param("id", id)
            .param("at", Timestamp.from(at))
            .param("outcome", if (answer == null) "failed" else "answered")
            .param("results", answer?.results)
            .param("cost", answer?.costMicros)
            .param("request", answer?.requestId)
            .update()
    }

    data class Answer(
        val results: Int,
        val costMicros: Long?,
        val requestId: String?,
    )

    private fun count(
        sql: String,
        since: Timestamp,
        accountId: UUID?,
    ): Int {
        val statement = jdbcClient.sql(sql).param("since", since)
        if (accountId != null) statement.param("account", accountId)
        return statement.query(Int::class.java).single()
    }
}
