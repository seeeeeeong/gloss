package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class PostCheckSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private data class Check(
        val account: UUID = READER,
        val job: UUID = UUID.randomUUID(),
        val attempt: Int = 1,
        val writing: Int = 1,
        val itemKey: String = "1.1 티켓 판매 애플리케이션",
        val finishReason: String = "stop",
        val passed: Boolean = true,
        val failed: String = "{}",
        val taken: Boolean = true,
    )

    private fun take(check: Check) =
        jdbc
            .sql(
                """
                insert into post_writing (account_id, job_id, writing, attempt, claimed_at)
                values (:account, :job, :writing, :attempt, now())
                """.trimIndent(),
            ).param("account", check.account)
            .param("job", check.job)
            .param("writing", check.writing)
            .param("attempt", check.attempt)
            .update()

    /** The check of a writing, taken first unless the check says otherwise. */
    private fun insert(check: Check): Int {
        if (check.taken) take(check)
        return judge(check)
    }

    private fun judge(check: Check) =
        jdbc
            .sql(
                """
                insert into post_check (job_id, attempt, writing, account_id, book_id, plan_id, item_key,
                                        prompt_version, finish_reason, passed, failed_checks, checked_at)
                values (:job, :attempt, :writing, :account, :book, :plan, :itemKey, 'post-1', :finishReason,
                        :passed, cast(:failed as text[]), now())
                """.trimIndent(),
            ).param("job", check.job)
            .param("attempt", check.attempt)
            .param("writing", check.writing)
            .param("account", check.account)
            .param("book", UUID.randomUUID())
            .param("plan", UUID.randomUUID())
            .param("itemKey", check.itemKey)
            .param("finishReason", check.finishReason)
            .param("passed", check.passed)
            .param("failed", check.failed)
            .update()

    private fun assertRefused(check: Check) {
        assertThatThrownBy { insert(check) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a writing that failed and the one written after it, in a later attempt, are both kept`() {
        val job = UUID.randomUUID()

        assertThatCode {
            insert(Check(job = job, passed = false, failed = "{title,opening,sections}"))
            insert(
                Check(
                    job = job,
                    attempt = 2,
                    writing = 2,
                    finishReason = "length",
                    passed = false,
                    failed = "{finish}",
                ),
            )
        }.doesNotThrowAnyException()
        val failed =
            jdbc
                .sql("select failed_checks from post_check where job_id = :job and writing = 1")
                .param("job", job)
                .query { rs, _ -> (rs.getArray(1).array as Array<*>).toList() }
                .single()
        assertThat(failed).containsExactly("title", "opening", "sections")
    }

    // One refused write per test: after it the transaction is aborted and refuses everything.
    @Test
    fun `a pass names no failure`() {
        assertRefused(Check(passed = true, failed = "{finish}"))
    }

    @Test
    fun `a fail names what failed`() {
        assertRefused(Check(passed = false))
    }

    @Test
    fun `a failure is one the check knows`() {
        assertRefused(Check(passed = false, failed = "{facts}"))
    }

    @Test
    fun `a failure is never null`() {
        assertRefused(Check(passed = false, failed = "{finish,NULL}"))
    }

    @Test
    fun `a post is written twice at most`() {
        assertRefused(Check(writing = 3))
    }

    @Test
    fun `a job's writing is judged once`() {
        val job = UUID.randomUUID()
        insert(Check(job = job, passed = false, failed = "{finish}"))

        assertThatThrownBy { judge(Check(job = job)) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a job's writing is taken once, whichever attempt takes it`() {
        val job = UUID.randomUUID()
        take(Check(job = job))

        assertThatThrownBy { take(Check(job = job, attempt = 2)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `only a writing taken is judged`() {
        assertRefused(Check(taken = false))
    }

    @Test
    fun `a taken writing is never given back`() {
        val job = UUID.randomUUID()
        take(Check(job = job))

        assertThatThrownBy {
            jdbc.sql("delete from post_writing where job_id = :job").param("job", job).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }

    @Test
    fun `another account's writings are its own`() {
        val job = UUID.randomUUID()
        insert(Check(job = job))

        assertThatCode { insert(Check(account = UUID.randomUUID(), job = job)) }.doesNotThrowAnyException()
    }

    @Test
    fun `a check belongs to an attempt`() {
        assertRefused(Check(attempt = 0))
    }

    @Test
    fun `a check names its section`() {
        assertRefused(Check(itemKey = ""))
    }

    @Test
    fun `a check is never changed`() {
        val job = UUID.randomUUID()
        insert(Check(job = job))

        assertThatThrownBy {
            jdbc.sql("update post_check set passed = false where job_id = :job").param("job", job).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }

    @Test
    fun `a check is never deleted`() {
        val job = UUID.randomUUID()
        insert(Check(job = job))

        assertThatThrownBy {
            jdbc.sql("delete from post_check where job_id = :job").param("job", job).update()
        }.isInstanceOf(DataAccessException::class.java).hasMessageContaining("append-only")
    }

    private companion object {
        val READER: UUID = UUID.randomUUID()
    }
}
