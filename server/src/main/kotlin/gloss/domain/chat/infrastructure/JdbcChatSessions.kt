package gloss.domain.chat.infrastructure

import gloss.domain.chat.domain.ChatSessionSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/** A reader's conversations about a book, read from their messages. */
@Repository
class JdbcChatSessions(
    private val jdbcClient: JdbcClient,
) {
    /** Newest activity first. A conversation is made with its first question, so each has one. */
    fun list(
        accountId: UUID,
        bookId: UUID,
    ): List<ChatSessionSummary> =
        jdbcClient
            .sql(
                """
                select s.id, s.created_at, first.text as first_question, first.item_key,
                       max(m.created_at) as last_activity_at,
                       array_remove(array_agg(distinct m.item_key), null) as item_keys
                from chat_session s
                join chat_message first on first.session_id = s.id and first.seq = 1
                join chat_message m on m.session_id = s.id
                where s.account_id = :account and s.book_id = :book
                group by s.id, s.created_at, first.text, first.item_key
                order by last_activity_at desc, s.id desc
                """.trimIndent(),
            ).param("account", accountId)
            .param("book", bookId)
            .query { row, _ -> summaryOf(row) }
            .list()

    /** The conversation with the newest activity; null before the first question. */
    fun latest(
        accountId: UUID,
        bookId: UUID,
    ): UUID? =
        jdbcClient
            .sql(
                """
                select s.id
                from chat_session s
                join chat_message m on m.session_id = s.id
                where s.account_id = :account and s.book_id = :book
                group by s.id
                order by max(m.created_at) desc, s.id desc
                limit 1
                """.trimIndent(),
            ).param("account", accountId)
            .param("book", bookId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)

    private fun summaryOf(row: ResultSet): ChatSessionSummary {
        @Suppress("UNCHECKED_CAST")
        val itemKeys = (row.getArray("item_keys").array as Array<String>).toList()
        return ChatSessionSummary(
            id = row.getObject("id", UUID::class.java),
            firstQuestion = row.getString("first_question"),
            itemKey = row.getString("item_key"),
            itemKeys = itemKeys,
            startedAt = row.getTimestamp("created_at").toInstant(),
            lastActivityAt = row.getTimestamp("last_activity_at").toInstant(),
        )
    }
}
