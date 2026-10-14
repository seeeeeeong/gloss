package gloss.post.application

import gloss.TestcontainersConfiguration
import gloss.plan.domain.event.PlanKept
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

// Not transactional: the event is delivered once its transaction commits. A plan and a post version are
// append-only, so the reader and the book made here stay.
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PostPlanListenerTest(
    @Autowired private val postService: PostService,
    @Autowired private val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val clock: Clock,
) {
    private val account: UUID = UUID.randomUUID()
    private val book: UUID = UUID.randomUUID()
    private val toc: UUID = UUID.randomUUID()

    private fun plan(versionNo: Int): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                       json_sha256, model, prompt_version, created_at)
                values (:id, 0, :account, :book, :toc, :versionNo, '{}', repeat('b', 64), 'model-x', 'plan-1', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account)
            .param("book", book)
            .param("toc", toc)
            .param("versionNo", versionNo)
            .update()
        return id
    }

    private fun shelf() {
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", account)
            .param("loginId", "s-" + account.toString().take(12))
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
        jdbc.sql("update toc set status = 'confirmed', confirmed_at = now() where id = :id").param("id", toc).update()
        jdbc
            .sql("update book set current_toc_id = :toc where id = :id")
            .param("toc", toc)
            .param("id", book)
            .update()
    }

    @Test
    fun `a book's new plan makes the posts written from its earlier one stale`() {
        shelf()
        postService.publish(
            PublishRequest(
                accountId = account,
                bookId = book,
                itemKey = "1장 객체",
                planId = plan(versionNo = 1),
                contentMd = "## 학습 목표\n\n본문이다.",
                finishReason = "stop",
                model = "model-x",
                promptVersion = "post-1",
                tokens = PostTokens(cacheHit = 0, cacheMiss = 100, completion = 200),
            ),
        )
        val newer = plan(versionNo = 2)

        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(
                PlanKept(
                    eventId = UUID.randomUUID(),
                    occurredAt = clock.instant(),
                    accountId = account,
                    bookId = book,
                    planId = newer,
                ),
            )
        }

        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        await().atMost(Duration.ofSeconds(10)).until {
            postService.freshness(account, book, "1장 객체") == PostFreshness.STALE
        }
    }
}
