package gloss.domain.account.infrastructure

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class JdbcLoginFailures(
    private val jdbcClient: JdbcClient,
) {
    /**
     * Held until commit: tries from one address are judged and recorded one at a time, so
     * neither its own limit nor a login id's can be outrun by sending them all at once.
     */
    fun lock(ip: String) {
        jdbcClient
            .sql("select 1 from pg_advisory_xact_lock(hashtextextended(:ip, 0))")
            .param("ip", ip)
            .query(Int::class.java)
            .single()
    }

    fun append(
        loginIdKey: String,
        ip: String,
        at: Instant,
    ) {
        jdbcClient
            .sql("insert into login_failure (login_id_key, ip, attempted_at) values (:key, cast(:ip as inet), :at)")
            .param("key", loginIdKey)
            .param("ip", ip)
            .param("at", Timestamp.from(at))
            .update()
    }

    /** Newest first, at most [limit]: enough to tell whether, and until when, a limit is full. */
    fun latestForLoginIdAndIp(
        loginIdKey: String,
        ip: String,
        since: Instant,
        limit: Int,
    ): List<Instant> =
        jdbcClient
            .sql(
                """
                select attempted_at from login_failure
                where login_id_key = :key and ip = cast(:ip as inet) and attempted_at > :since
                order by attempted_at desc
                limit :limit
                """.trimIndent(),
            ).param("key", loginIdKey)
            .param("ip", ip)
            .param("since", Timestamp.from(since))
            .param("limit", limit)
            .query { rs, _ -> rs.getTimestamp(1).toInstant() }
            .list()

    fun latestForIp(
        ip: String,
        since: Instant,
        limit: Int,
    ): List<Instant> =
        jdbcClient
            .sql(
                """
                select attempted_at from login_failure
                where ip = cast(:ip as inet) and attempted_at > :since
                order by attempted_at desc
                limit :limit
                """.trimIndent(),
            ).param("ip", ip)
            .param("since", Timestamp.from(since))
            .param("limit", limit)
            .query { rs, _ -> rs.getTimestamp(1).toInstant() }
            .list()
}
