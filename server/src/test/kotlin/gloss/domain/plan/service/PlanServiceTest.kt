package gloss.domain.plan.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.generation.domain.job.JobState
import gloss.domain.generation.domain.job.JobStatus
import gloss.domain.plan.domain.BookPlanDraft
import gloss.domain.plan.domain.event.PlanKept
import gloss.domain.plan.dto.response.PlanStatusResponse
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
@RecordApplicationEvents
class PlanServiceTest(
    @Autowired private val planService: PlanService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    /** A reader's book with contents, made as the book module would have left them. Rolled back with the test. */
    private inner class Shelf(
        private val confirmedAgo: String = "0 seconds",
    ) {
        val account: UUID = UUID.randomUUID()
        val book: UUID = UUID.randomUUID()
        val toc: UUID = UUID.randomUUID()

        init {
            jdbc
                .sql(
                    """
                    insert into account (id, version, login_id, display_name, password_hash, role, status,
                                         must_change_password, session_epoch, created_at)
                    values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                    """.trimIndent(),
                ).param("id", account)
                .param("loginId", "p-" + account.toString().take(12))
                .update()
            jdbc
                .sql(
                    """
                    insert into book (id, version, account_id, title, authors, source, created_at)
                    values (:id, 0, :account, '오브젝트', '{}', 'manual', now())
                    """.trimIndent(),
                ).param("id", book)
                .param("account", account)
                .update()
            jdbc
                .sql(
                    """
                    insert into toc (id, version, account_id, book_id, version_no, origin, raw, raw_sha256, status, created_at)
                    values (:id, 0, :account, :book, 1, 'manual', '1장', repeat('a', 64), 'draft', now())
                    """.trimIndent(),
                ).param("id", toc)
                .param("account", account)
                .param("book", book)
                .update()
            use(toc)
        }

        fun use(tocId: UUID) {
            jdbc
                .sql(
                    """
                    update toc set status = 'confirmed', confirmed_at = now() - cast(:ago as interval)
                    where id = :id and status = 'draft'
                    """.trimIndent(),
                ).param("ago", confirmedAgo)
                .param("id", tocId)
                .update()
            jdbc
                .sql(
                    "update book set current_toc_id = :toc where id = :id",
                ).param("toc", tocId)
                .param("id", book)
                .update()
        }

        fun newerToc(): UUID {
            val newer = UUID.randomUUID()
            jdbc
                .sql(
                    """
                    insert into toc (id, version, account_id, book_id, version_no, origin, raw, raw_sha256, status, created_at)
                    values (:id, 0, :account, :book, 2, 'manual', '2장', repeat('c', 64), 'draft', now())
                    """.trimIndent(),
                ).param("id", newer)
                .param("account", account)
                .param("book", book)
                .update()
            return newer
        }

        fun draft(json: String = """{"audience":"독자"}""") =
            BookPlanDraft(
                accountId = account,
                bookId = book,
                tocId = toc,
                canonicalJson = json,
                model = "deepseek-flash",
                promptVersion = "plan-1",
            )
    }

    @Test
    fun `a plan is kept as the contents' first, with the hash of its bytes, and said to be kept`(
        @Autowired events: ApplicationEvents,
    ) {
        val shelf = Shelf()

        val id = planService.keep(shelf.draft())

        entityManager.flush()
        val row =
            jdbc
                .sql(
                    """
                    select version_no, canonical_json, model, prompt_version,
                           json_sha256 = encode(sha256(convert_to(canonical_json, 'UTF8')), 'hex') as hashed
                    from book_plan where id = :id
                    """.trimIndent(),
                ).param("id", id)
                .query()
                .singleRow()
        assertThat(row.values).containsExactly(1, """{"audience":"독자"}""", "deepseek-flash", "plan-1", true)
        assertThat(planService.exists(shelf.account, shelf.toc)).isTrue()
        val said = events.stream(PlanKept::class.java).toList().single()
        assertThat(listOf(said.accountId, said.bookId, said.planId)).containsExactly(shelf.account, shelf.book, id)
    }

    @Test
    fun `contents that have a plan keep it, and a second is not added`() {
        val shelf = Shelf()
        planService.keep(shelf.draft())

        val second = planService.keep(shelf.draft("""{"audience":"다른 독자"}"""))

        entityManager.flush()
        assertThat(second).isNull()
        val kept =
            jdbc
                .sql("select count(*) from book_plan where toc_id = :toc")
                .param("toc", shelf.toc)
                .query(Int::class.java)
                .single()
        assertThat(kept).isEqualTo(1)
    }

    @Test
    fun `a kept plan is found with its bytes and each item's kind, and only by its reader`() {
        val shelf = Shelf()
        val json =
            """{"audience":"독자","items":[{"i":0,"kind":"overview"},{"i":1,"kind":"none"},{"i":2,"kind":"lesson"}]}"""
        val id = planService.keep(shelf.draft(json))

        val plan = checkNotNull(planService.find(shelf.account, shelf.toc))

        assertThat(plan.id).isEqualTo(id)
        assertThat(plan.canonicalJson).isEqualTo(json)
        assertThat(plan.kinds).containsExactly(PlanItemKind.OVERVIEW, PlanItemKind.NONE, PlanItemKind.LESSON)
        assertThat(planService.find(UUID.randomUUID(), shelf.toc)).isNull()
        assertThat(planService.find(shelf.account, shelf.newerToc())).isNull()
    }

    @Test
    fun `the job writing a plan says how it stands, and contents never asked for have none`() {
        val shelf = Shelf()
        assertThat(planService.progress(shelf.account, shelf.toc)).isNull()

        planService.request(shelf.account, shelf.book, shelf.toc)

        assertThat(planService.progress(shelf.account, shelf.toc)).isEqualTo(JobStatus(JobState.QUEUED, null))
    }

    @Test
    fun `an event's request asks only for contents never asked for, while a reader's own asks again`() {
        val shelf = Shelf()
        val first = planService.requestOnce(shelf.account, shelf.book, shelf.toc)
        jdbc
            .sql(
                """
                update generation_job set state = 'failed', error_kind = 'plan.missing_item', finished_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", first)
            .update()

        val delivered = planService.requestOnce(shelf.account, shelf.book, shelf.toc)
        val asked = planService.request(shelf.account, shelf.book, shelf.toc)

        assertThat(first).isNotNull()
        assertThat(delivered).isNull()
        assertThat(asked).isNotNull().isNotEqualTo(first)
    }

    @Test
    fun `a plan of contents no longer in use is not kept`() {
        val shelf = Shelf()
        shelf.use(shelf.newerToc())

        assertThat(planService.keep(shelf.draft())).isNull()
        assertThat(planService.exists(shelf.account, shelf.toc)).isFalse()
    }

    @Test
    fun `asking for a plan twice is one job, which carries the book and its contents`() {
        val shelf = Shelf()

        val first = planService.request(shelf.account, shelf.book, shelf.toc)
        val again = planService.request(shelf.account, shelf.book, shelf.toc)

        assertThat(again).isEqualTo(first).isNotNull()
        val job =
            jdbc
                .sql(
                    """
                    select kind, dedupe_key, payload ->> 'bookId' as book, payload ->> 'tocId' as toc
                    from generation_job where id = :id
                    """.trimIndent(),
                ).param("id", first)
                .query()
                .singleRow()
        assertThat(job.values)
            .containsExactly("plan", "plan:toc:${shelf.toc}", shelf.book.toString(), shelf.toc.toString())
    }

    @Test
    fun `contents that have a plan are not asked for again, and another reader's plan is not theirs`() {
        val shelf = Shelf()
        planService.keep(shelf.draft())

        assertThat(planService.request(shelf.account, shelf.book, shelf.toc)).isNull()
        assertThat(planService.exists(UUID.randomUUID(), shelf.toc)).isFalse()
    }

    @Test
    fun `a reader waiting on a book's plan sees it planning, then failed with why, or ready`() {
        val shelf = Shelf()
        val waiting = planService.status(shelf.account, shelf.book)
        val job = planService.request(shelf.account, shelf.book, shelf.toc)
        val asked = planService.status(shelf.account, shelf.book)
        jdbc
            .sql(
                """
                update generation_job set state = 'failed', error_kind = 'budget.limit_reached', finished_at = now()
                where id = :id
                """.trimIndent(),
            ).param("id", job)
            .update()
        val failed = planService.status(shelf.account, shelf.book)
        planService.keep(shelf.draft())

        assertThat(waiting).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(asked).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(failed).isEqualTo(PlanStatusResponse.failed("budget.limit_reached"))
        assertThat(planService.status(shelf.account, shelf.book)).isEqualTo(PlanStatusResponse.READY)
    }

    @Test
    fun `a plan job cancelled, as for an account disabled while it waited, is failed, not planning forever`() {
        val shelf = Shelf()
        val job = planService.request(shelf.account, shelf.book, shelf.toc)
        jdbc
            .sql("update generation_job set state = 'cancelled', finished_at = now() where id = :id")
            .param("id", job)
            .update()

        assertThat(planService.status(shelf.account, shelf.book)).isEqualTo(PlanStatusResponse.failed("plan.cancelled"))
    }

    @Test
    fun `contents confirmed a while ago with no plan job asked for are failed, and a retry asks for one`() {
        val fresh = Shelf()
        val lost = Shelf(confirmedAgo = "2 minutes")

        assertThat(planService.status(fresh.account, fresh.book)).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(planService.status(lost.account, lost.book))
            .isEqualTo(PlanStatusResponse.failed("plan.not_started"))
        assertThat(planService.retry(lost.account, lost.book)).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(planService.status(lost.account, lost.book)).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(jobs(lost)).isEqualTo(1)
    }

    @Test
    fun `a retry asks again for a plan that ended without one, and adds nothing to one still to come`() {
        val shelf = Shelf()
        val first = planService.request(shelf.account, shelf.book, shelf.toc)
        val waiting = planService.retry(shelf.account, shelf.book)
        val queued = jobs(shelf)
        jdbc
            .sql("update generation_job set state = 'cancelled', finished_at = now() where id = :id")
            .param("id", first)
            .update()

        val asked = planService.retry(shelf.account, shelf.book)

        assertThat(waiting).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(queued).isEqualTo(1)
        assertThat(asked).isEqualTo(PlanStatusResponse.PLANNING)
        assertThat(jobs(shelf)).isEqualTo(2)
        assertThat(planService.status(shelf.account, shelf.book)).isEqualTo(PlanStatusResponse.PLANNING)
        planService.keep(shelf.draft())
        assertThat(planService.retry(shelf.account, shelf.book)).isEqualTo(PlanStatusResponse.READY)
        assertThat(jobs(shelf)).isEqualTo(2)
    }

    private fun jobs(shelf: Shelf): Int =
        jdbc
            .sql("select count(*) from generation_job where account_id = :account")
            .param("account", shelf.account)
            .query(Int::class.java)
            .single()

    @Test
    fun `another reader's book, or one gone, has no plan to wait on`() {
        val shelf = Shelf()

        assertThatThrownBy { planService.status(UUID.randomUUID(), shelf.book) }
            .isInstanceOfSatisfying(GlossException::class.java) {
                assertThat(it.error).isEqualTo(PlanError.BOOK_NOT_FOUND)
            }
    }
}
