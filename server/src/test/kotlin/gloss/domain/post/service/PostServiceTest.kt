package gloss.domain.post.service

import gloss.TestcontainersConfiguration
import gloss.common.error.GlossException
import gloss.domain.post.domain.PostContent
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class PostServiceTest(
    @Autowired private val postService: PostService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    private val itemKey = "1장 객체 > 1.1 tcp/ip"

    /** A reader's book with a plan for it, made as generation would have left them. */
    private inner class Shelf {
        val account: UUID = UUID.randomUUID()
        val book: UUID = UUID.randomUUID()
        val plan: UUID = UUID.randomUUID()

        init {
            val toc = UUID.randomUUID()
            jdbc
                .sql(
                    """
                    insert into account (id, version, login_id, display_name, password_hash, role, status,
                                         must_change_password, session_epoch, created_at)
                    values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                    """.trimIndent(),
                ).param("id", account)
                .param("loginId", "w-" + account.toString().take(12))
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
            jdbc
                .sql(
                    """
                    insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                           json_sha256, model, prompt_version, created_at)
                    values (:id, 0, :account, :book, :toc, 1, '{}', repeat('b', 64), 'model-x', 'p1', now())
                    """.trimIndent(),
                ).param("id", plan)
                .param("account", account)
                .param("book", book)
                .param("toc", toc)
                .update()
        }

        fun writing(
            content: String = "# 객체\n\n본문이다.",
            key: String = itemKey,
            planId: UUID = plan,
        ) = PublishRequest(
            accountId = account,
            bookId = book,
            itemKey = key,
            planId = planId,
            contentMd = content,
            finishReason = "stop",
            model = "model-x",
            promptVersion = "p1",
            tokens = PostTokens(cacheHit = 900, cacheMiss = 100, completion = 1_200),
        )
    }

    private fun versionsOf(postId: UUID): Int =
        jdbc
            .sql("select count(*) from post_version where post_id = :id")
            .param("id", postId)
            .query(Int::class.java)
            .single()

    private fun assertNotFound(call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(PostError.POST_NOT_FOUND)
        }
    }

    @Test
    fun `a finished writing becomes the post's first version, the one a reader gets`() {
        val shelf = Shelf()

        val published = postService.publish(shelf.writing())
        val post = postService.get(shelf.account, shelf.book, itemKey)

        assertThat(published.created).isTrue()
        assertThat(published.versionNo).isEqualTo(1)
        assertThat(post.id).isEqualTo(published.postId)
        assertThat(post.versionId).isEqualTo(published.versionId)
        assertThat(post.contentMd).isEqualTo("# 객체\n\n본문이다.")
        assertThat(post.model).isEqualTo("model-x")
        assertThat(post.stale).isFalse()
    }

    @Test
    fun `a section has a post once one is published, for its reader alone`() {
        val shelf = Shelf()
        val before = postService.freshness(shelf.account, shelf.book, itemKey)

        postService.publish(shelf.writing())

        assertThat(before).isEqualTo(PostFreshness.MISSING)
        assertThat(postService.freshness(shelf.account, shelf.book, itemKey)).isEqualTo(PostFreshness.FRESH)
        assertThat(postService.freshness(UUID.randomUUID(), shelf.book, itemKey)).isEqualTo(PostFreshness.MISSING)
        assertThat(postService.freshness(shelf.account, shelf.book, "2장 설계")).isEqualTo(PostFreshness.MISSING)
    }

    @Test
    fun `a post is stale once the book has another plan, and fresh again once rewritten`() {
        val shelf = Shelf()
        postService.publish(shelf.writing())

        postService.outdate(shelf.account, shelf.book, shelf.plan)
        val sameplan = postService.freshness(shelf.account, shelf.book, itemKey)
        postService.outdate(shelf.account, shelf.book, UUID.randomUUID())
        val otherPlan = postService.freshness(shelf.account, shelf.book, itemKey)
        val shown = postService.get(shelf.account, shelf.book, itemKey)
        postService.publish(shelf.writing(content = "## 학습 목표\n\n다시 쓴 본문이다."))

        assertThat(sameplan).isEqualTo(PostFreshness.FRESH)
        assertThat(otherPlan).isEqualTo(PostFreshness.STALE)
        assertThat(shown.stale).isTrue()
        assertThat(postService.freshness(shelf.account, shelf.book, itemKey)).isEqualTo(PostFreshness.FRESH)
    }

    @Test
    fun `a rewrite is the next version and replaces what the reader sees`() {
        val shelf = Shelf()
        val first = postService.publish(shelf.writing())

        val second = postService.publish(shelf.writing(content = "# 객체\n\n다시 쓴 본문이다."))
        val post = postService.get(shelf.account, shelf.book, itemKey)

        assertThat(second.postId).isEqualTo(first.postId)
        assertThat(second.versionNo).isEqualTo(2)
        assertThat(post.versionNo).isEqualTo(2)
        assertThat(post.contentMd).isEqualTo("# 객체\n\n다시 쓴 본문이다.")
    }

    @Test
    fun `the same content as the version before adds nothing`() {
        val shelf = Shelf()
        val first = postService.publish(shelf.writing())

        val again = postService.publish(shelf.writing())
        entityManager.flush()

        assertThat(again.created).isFalse()
        assertThat(again.versionId).isEqualTo(first.versionId)
        assertThat(versionsOf(first.postId)).isEqualTo(1)
    }

    @Test
    fun `the same content written from another plan is that plan's version, and the post is fresh`() {
        val shelf = Shelf()
        val first = postService.publish(shelf.writing())
        val newer = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book_plan (id, version, account_id, book_id, toc_id, version_no, canonical_json,
                                       json_sha256, model, prompt_version, created_at)
                select :id, 0, account_id, book_id, toc_id, 2, '{}', repeat('c', 64), model, prompt_version, now()
                from book_plan where id = :plan
                """.trimIndent(),
            ).param("id", newer)
            .param("plan", shelf.plan)
            .update()
        postService.outdate(shelf.account, shelf.book, newer)

        val again = postService.publish(shelf.writing(planId = newer))

        assertThat(again.created).isTrue()
        assertThat(again.versionNo).isEqualTo(2)
        assertThat(postService.freshness(shelf.account, shelf.book, itemKey)).isEqualTo(PostFreshness.FRESH)
        assertThat(first.versionNo).isEqualTo(1)
    }

    @Test
    fun `a stale post is fresh again once rewritten`() {
        val shelf = Shelf()
        val first = postService.publish(shelf.writing())
        entityManager.flush()
        jdbc.sql("update post set stale = true where id = :id").param("id", first.postId).update()
        entityManager.clear()
        val wasStale = postService.get(shelf.account, shelf.book, itemKey).stale

        postService.publish(shelf.writing(content = "# 객체\n\n새 plan으로 쓴 본문이다."))

        assertThat(wasStale).isTrue()
        assertThat(postService.get(shelf.account, shelf.book, itemKey).stale).isFalse()
    }

    @Test
    fun `a book lists the sections that have a post, each with its current version`() {
        val shelf = Shelf()
        postService.publish(shelf.writing())
        postService.publish(shelf.writing(content = "# 객체\n\n다시 쓴 본문이다."))
        postService.publish(shelf.writing(key = "2장 설계"))
        Shelf().let { postService.publish(it.writing()) }

        val posts = postService.list(shelf.account, shelf.book)

        assertThat(posts.map { it.itemKey to it.versionNo }).containsExactlyInAnyOrder(itemKey to 2, "2장 설계" to 1)
    }

    @Test
    fun `a section nothing was written for has no post`() {
        val shelf = Shelf()
        postService.publish(shelf.writing())

        assertNotFound { postService.get(shelf.account, shelf.book, "2장 설계") }
    }

    @Test
    fun `another reader finds no post, and none in the list`() {
        val shelf = Shelf()
        postService.publish(shelf.writing())
        val stranger = Shelf().account

        assertNotFound { postService.get(stranger, shelf.book, itemKey) }
        assertThat(postService.list(stranger, shelf.book)).isEmpty()
    }

    @Test
    fun `a post with no version yet is not there to read`() {
        val shelf = Shelf()
        jdbc
            .sql(
                """
                insert into post (id, version, account_id, book_id, item_key, stale, created_at)
                values (:id, 0, :account, :book, :itemKey, false, now())
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("account", shelf.account)
            .param("book", shelf.book)
            .param("itemKey", itemKey)
            .update()

        assertNotFound { postService.get(shelf.account, shelf.book, itemKey) }
        assertThat(postService.list(shelf.account, shelf.book)).isEmpty()
    }

    @Test
    fun `an answer that was cut off is never kept`() {
        val shelf = Shelf()

        assertThatThrownBy { postService.publish(shelf.writing().copy(finishReason = "length")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `content that is blank or past the limit is never kept`() {
        val shelf = Shelf()

        assertThatThrownBy { postService.publish(shelf.writing(content = " \n")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { postService.publish(shelf.writing(content = "가".repeat(PostContent.MAX_LENGTH + 1))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a key that is empty or past the limit is never kept`() {
        val shelf = Shelf()

        assertThatThrownBy { postService.publish(shelf.writing(key = "")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { postService.publish(shelf.writing(key = "가".repeat(601))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a writing for another reader's book is refused`() {
        val shelf = Shelf()
        val stranger = Shelf()

        assertThatThrownBy {
            postService.publish(shelf.writing().copy(accountId = stranger.account))
            entityManager.flush()
        }.hasMessageContaining("post_book_id_account_id_fkey")
    }

    @Test
    fun `a writing from another book's plan is refused`() {
        val shelf = Shelf()
        val other = Shelf()

        assertThatThrownBy {
            postService.publish(shelf.writing().copy(planId = other.plan))
            entityManager.flush()
        }.hasMessageContaining("post_version_plan_id_book_id_fkey")
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `two writings of one section at once make one post with two versions`() {
        val shelf = Shelf()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val published =
            listOf("# 객체\n\n첫 본문이다.", "# 객체\n\n둘째 본문이다.").map { content ->
                pool.submit<PublishedVersion> {
                    start.await(5, TimeUnit.SECONDS)
                    postService.publish(shelf.writing(content = content))
                }
            }
        start.countDown()

        val versions = published.map { it.get(10, TimeUnit.SECONDS) }

        assertThat(versions.map { it.postId }.distinct()).hasSize(1)
        assertThat(versions.map { it.versionNo }).containsExactlyInAnyOrder(1, 2)
        assertThat(postService.get(shelf.account, shelf.book, itemKey).versionNo).isEqualTo(2)
        pool.shutdown()
    }
}
