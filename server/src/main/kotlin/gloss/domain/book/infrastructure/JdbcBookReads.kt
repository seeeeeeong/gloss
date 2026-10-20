package gloss.domain.book.infrastructure

import gloss.domain.book.domain.BookRead
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Where a reader last was in each of their books, one row a book. */
@Repository
class JdbcBookReads(
    private val jdbcClient: JdbcClient,
) {
    fun keep(
        accountId: UUID,
        bookId: UUID,
        itemKey: String,
        at: Instant,
    ) {
        jdbcClient
            .sql(
                """
                insert into book_read (book_id, account_id, item_key, read_at)
                values (:book, :account, :itemKey, :at)
                on conflict (book_id) do update set item_key = excluded.item_key, read_at = excluded.read_at
                """.trimIndent(),
            ).param("book", bookId)
            .param("account", accountId)
            .param("itemKey", itemKey)
            .param("at", Timestamp.from(at))
            .update()
    }

    fun find(
        accountId: UUID,
        bookId: UUID,
    ): BookRead? =
        jdbcClient
            .sql("$READS where r.account_id = :account and r.book_id = :book and b.archived_at is null")
            .param("account", accountId)
            .param("book", bookId)
            .query { row, _ -> readOf(row) }
            .optional()
            .orElse(null)

    /** Newest first. */
    fun recent(
        accountId: UUID,
        limit: Int,
    ): List<BookRead> =
        jdbcClient
            .sql(
                "$READS where r.account_id = :account and b.archived_at is null" +
                    " order by r.read_at desc, r.book_id desc limit :limit",
            ).param("account", accountId)
            .param("limit", limit)
            .query { row, _ -> readOf(row) }
            .list()

    private fun readOf(row: ResultSet) =
        BookRead(
            bookId = row.getObject("book_id", UUID::class.java),
            title = row.getString("title"),
            itemKey = row.getString("item_key"),
            itemNumber = row.getString("number"),
            itemTitle = row.getString("item_title"),
            readAt = row.getTimestamp("read_at").toInstant(),
        )

    private companion object {
        val READS =
            """
            select r.book_id, b.title, r.item_key, i.number, i.title as item_title, r.read_at
            from book_read r
            join book b on b.id = r.book_id and b.account_id = r.account_id
            left join toc_item i on i.toc_id = b.current_toc_id and i.item_key = r.item_key
            """.trimIndent()
    }
}
