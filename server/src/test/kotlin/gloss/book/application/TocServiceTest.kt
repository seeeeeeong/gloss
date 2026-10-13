package gloss.book.application

import gloss.TestcontainersConfiguration
import gloss.book.application.dto.BookCreateRequest
import gloss.book.application.dto.TocReviseRequest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class TocServiceTest(
    @Autowired private val tocService: TocService,
    @Autowired private val bookService: BookService,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val transactionTemplate: TransactionTemplate,
) {
    private val contents = "1장 객체, 설계\n1.1 티켓 판매 애플리케이션\n1.2 무엇이 문제인가\n2장 객체지향 프로그래밍"

    // Rolled back with the test; no one signs in, so the hash is never read.
    private fun accountId(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "t-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(account: UUID) =
        bookService.create(
            account,
            BookCreateRequest(title = "오브젝트", authors = listOf("조영호"), publisher = "위키북스", toc = contents),
        )

    @Test
    fun `the outline is the book and its confirmed contents in order, each item under its parent's place`() {
        val account = accountId()
        val created = book(account)
        bookService.confirmToc(account, created.id, created.toc.id)

        val outline = checkNotNull(tocService.outline(account, created.id))

        assertThat(outline.tocId).isEqualTo(created.toc.id)
        assertThat(outline.title).isEqualTo("오브젝트")
        assertThat(outline.authors).containsExactly("조영호")
        assertThat(outline.items.map { "${it.ord} ${it.parent} ${it.depth} ${it.number} ${it.title}" }).containsExactly(
            "0 null 1 1장 객체, 설계",
            "1 0 2 1.1 티켓 판매 애플리케이션",
            "2 0 2 1.2 무엇이 문제인가",
            "3 null 1 2장 객체지향 프로그래밍",
        )
        assertThat(outline.items.map { it.itemKey }).doesNotHaveDuplicates()
    }

    @Test
    fun `a draft edited since does not change the outline`() {
        val account = accountId()
        val created = book(account)
        bookService.confirmToc(account, created.id, created.toc.id)
        bookService.reviseToc(account, created.id, TocReviseRequest("1장 전혀 다른 목차"))

        val outline = checkNotNull(tocService.outline(account, created.id))

        assertThat(outline.tocId).isEqualTo(created.toc.id)
        assertThat(outline.items).hasSize(4)
        assertThat(tocService.holdCurrentTocId(account, created.id)).isEqualTo(created.toc.id)
    }

    @Test
    fun `there is no outline before a confirm, nor of another reader's book`() {
        val account = accountId()
        val unconfirmed = book(account)
        val other = accountId()
        val theirs = book(other)
        bookService.confirmToc(other, theirs.id, theirs.toc.id)

        assertThat(tocService.outline(account, unconfirmed.id)).isNull()
        assertThat(tocService.outline(account, theirs.id)).isNull()
        assertThat(tocService.holdCurrentTocId(account, unconfirmed.id)).isNull()
        assertThat(tocService.holdCurrentTocId(account, theirs.id)).isNull()
    }

    // Committed, as a job's write and a reader's confirm are: nothing here is rolled back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `a confirm of another version waits for whoever holds the contents in use`() {
        val account = accountId()
        val created = book(account)
        bookService.confirmToc(account, created.id, created.toc.id)
        val revised = bookService.reviseToc(account, created.id, TocReviseRequest("1장 다시 쓴 목차"))
        val pool = Executors.newSingleThreadExecutor()

        val confirming =
            transactionTemplate.execute {
                val held = tocService.holdCurrentTocId(account, created.id)
                val other =
                    pool.submit<UUID?> {
                        bookService
                            .confirmToc(
                                account,
                                created.id,
                                revised.toc.id,
                            ).currentTocId
                    }
                await().atMost(Duration.ofSeconds(10)).until { waitingOnALock() }
                assertThat(held).isEqualTo(created.toc.id)
                assertThat(other.isDone).isFalse()
                other
            }

        assertThat(checkNotNull(confirming).get(10, TimeUnit.SECONDS)).isEqualTo(revised.toc.id)
        pool.shutdown()
    }

    private fun waitingOnALock(): Boolean =
        jdbc
            .sql(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()",
            ).query(Long::class.java)
            .single() > 0
}
