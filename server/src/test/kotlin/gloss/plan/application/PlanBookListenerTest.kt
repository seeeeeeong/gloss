package gloss.plan.application

import gloss.TestcontainersConfiguration
import gloss.account.Members
import gloss.book.domain.event.TocConfirmed
import gloss.generation.newReader
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PlanBookListenerTest(
    @Autowired private val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {
    private val account = Members(jdbc, passwordEncoder, clock).newReader(jdbc)

    // The worker does not run in tests, so the job waits; left behind, another test's worker would take it.
    @AfterEach
    fun cleanUp() {
        jdbc.sql("delete from generation_job where account_id = :account").param("account", account).update()
    }

    private fun keys(): List<String?> =
        jdbc
            .sql("select dedupe_key from generation_job where account_id = :account and state = 'queued'")
            .param("account", account)
            .query(String::class.java)
            .list()

    @Test
    fun `confirmed contents are given a plan job, once the confirm has committed`() {
        val tocId = UUID.randomUUID()

        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(
                TocConfirmed(
                    eventId = UUID.randomUUID(),
                    occurredAt = clock.instant(),
                    accountId = account,
                    bookId = UUID.randomUUID(),
                    tocId = tocId,
                ),
            )
            assertThat(keys()).isEmpty()
        }

        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        await().atMost(Duration.ofSeconds(10)).until { keys() == listOf("plan:toc:$tocId") }
    }
}
