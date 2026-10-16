package gloss.domain.post.infrastructure

import gloss.domain.post.domain.WritingClaim
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

/** The writings jobs have taken. Append-only: a writing taken stays taken, called or not. */
@Repository
class JdbcPostWritings(
    private val jdbcClient: JdbcClient,
) {
    fun taken(
        accountId: UUID,
        jobId: UUID,
    ): Int =
        jdbcClient
            .sql("select count(*) from post_writing where account_id = :account and job_id = :job")
            .param("account", accountId)
            .param("job", jobId)
            .query(Int::class.java)
            .single()

    /** False when the writing was already taken, by this attempt or another. */
    fun take(claim: WritingClaim): Boolean =
        jdbcClient
            .sql(
                """
                insert into post_writing (account_id, job_id, writing, attempt, claimed_at)
                values (:account, :job, :writing, :attempt, :claimedAt)
                on conflict (account_id, job_id, writing) do nothing
                """.trimIndent(),
            ).param("account", claim.accountId)
            .param("job", claim.jobId)
            .param("writing", claim.writing)
            .param("attempt", claim.attempt)
            .param("claimedAt", Timestamp.from(claim.claimedAt))
            .update() == 1
}
