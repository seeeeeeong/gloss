package gloss.plan.application

import gloss.TestcontainersConfiguration
import gloss.plan.domain.BookPlanDraft
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class PlanServiceTest(
    @Autowired private val planService: PlanService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    /** A reader's book with contents, made as the book module would have left them. Rolled back with the test. */
    private inner class Shelf {
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
                .sql("update toc set status = 'confirmed', confirmed_at = now() where id = :id and status = 'draft'")
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
    fun `a plan is kept as the contents' first, with the hash of its bytes`() {
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
}
