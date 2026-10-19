package gloss.domain.setup.infrastructure

import gloss.domain.setup.domain.SetupPolicy
import gloss.domain.setup.domain.SetupSessionSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/** A reader's setup sessions as their list shows them, read across each session's messages. */
@Repository
class JdbcSetupSessions(
    private val jdbcClient: JdbcClient,
) {
    /**
     * Only sessions the reader said something in, latest activity first, at most [limit]; only those
     * that made [bookId] when it is named.
     */
    fun recent(
        accountId: UUID,
        bookId: UUID?,
        limit: Int,
    ): List<SetupSessionSummary> =
        jdbcClient
            .sql(
                """
                select s.id, s.state, s.held_title, s.book_id, s.created_at,
                       greatest(s.updated_at, activity.latest) as last_activity_at,
                       opening.text as first_text
                from setup_session s
                cross join lateral (
                    select max(m.created_at) as latest, bool_or(m.role = 'reader') as spoke
                    from setup_message m
                    where m.session_id = s.id and m.account_id = s.account_id
                ) activity
                left join lateral (
                    select m.payload ->> 'text' as text
                    from setup_message m
                    where m.session_id = s.id and m.account_id = s.account_id and m.kind = 'text'
                    order by m.seq
                    limit 1
                ) opening on true
                where s.account_id = :account
                  and (cast(:book as uuid) is null or s.book_id = cast(:book as uuid))
                  and activity.spoke
                order by last_activity_at desc, s.id desc
                limit :limit
                """.trimIndent(),
            ).param("account", accountId)
            .param("book", bookId)
            .param("limit", limit)
            .query { row, _ -> summaryOf(row) }
            .list()

    private fun summaryOf(row: ResultSet): SetupSessionSummary {
        val state = row.getString("state")
        return SetupSessionSummary(
            id = row.getObject("id", UUID::class.java),
            state = SetupPolicy.State.entries.first { it.code == state },
            heldTitle = row.getString("held_title"),
            firstText = row.getString("first_text"),
            bookId = row.getObject("book_id", UUID::class.java),
            createdAt = row.getTimestamp("created_at").toInstant(),
            lastActivityAt = row.getTimestamp("last_activity_at").toInstant(),
        )
    }
}
