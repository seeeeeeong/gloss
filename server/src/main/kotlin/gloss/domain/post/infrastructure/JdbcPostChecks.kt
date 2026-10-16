package gloss.domain.post.infrastructure

import gloss.domain.post.domain.CheckedWriting
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

/** How each writing of a post was judged. Append-only: a judgement made stays made. */
@Repository
class JdbcPostChecks(
    private val jdbcClient: JdbcClient,
) {
    fun append(checked: CheckedWriting) {
        // An array literal: the codes are this application's own words, with no comma or quote in them.
        val failed = checked.verdict.failures.joinToString(separator = ",", prefix = "{", postfix = "}") { it.code }
        jdbcClient
            .sql(
                """
                insert into post_check (job_id, attempt, writing, account_id, book_id, plan_id, item_key,
                                        prompt_version, finish_reason, passed, failed_checks, checked_at)
                values (:job, :attempt, :writing, :account, :book, :plan, :itemKey, :promptVersion,
                        :finishReason, :passed, cast(:failed as text[]), :checkedAt)
                """.trimIndent(),
            ).param("job", checked.jobId)
            .param("attempt", checked.attempt)
            .param("writing", checked.writing)
            .param("account", checked.accountId)
            .param("book", checked.bookId)
            .param("plan", checked.planId)
            .param("itemKey", checked.itemKey)
            .param("promptVersion", checked.promptVersion)
            .param("finishReason", checked.finishReason)
            .param("passed", checked.verdict.passed)
            .param("failed", failed)
            .param("checkedAt", Timestamp.from(checked.checkedAt))
            .update()
    }
}
