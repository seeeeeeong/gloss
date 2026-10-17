package gloss.domain.setup.infrastructure

import gloss.domain.setup.domain.SetupSession
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/**
 * The pages detailed contents were found on. Append-only, and never the page's text: only where it is,
 * what it is called, when it was read and a hash of what the check read, so a later look can tell
 * whether the page still says the same.
 */
@Repository
class JdbcSourcePages(
    private val jdbcClient: JdbcClient,
) {
    /** A page read at [fetchedAt], whose [text] the contents were checked against. */
    data class Read(
        val url: String,
        val title: String,
        val text: String,
        val fetchedAt: Instant,
    )

    /** In the caller's transaction, which holds [session]'s lock. */
    fun add(
        session: SetupSession,
        page: Read,
        id: UUID,
    ) {
        jdbcClient
            .sql(
                """
                insert into source_page (id, account_id, session_id, url, title, fetched_at, text_sha256, characters)
                values (:id, :account, :session, :url, :title, :fetched, :sha, :characters)
                """.trimIndent(),
            ).param("id", id)
            .param("account", session.accountId)
            .param("session", session.id)
            .param("url", page.url)
            .param("title", page.title)
            .param("fetched", Timestamp.from(page.fetchedAt))
            .param("sha", sha256(page.text))
            .param("characters", page.text.codePointCount(0, page.text.length))
            .update()
    }

    private fun sha256(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
}
