package gloss.domain.generation.service

import gloss.TestcontainersConfiguration
import gloss.domain.account.Members
import gloss.domain.account.domain.event.AccountDisabled
import gloss.domain.generation.domain.job.JobKind
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.newReader
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
class GenerationAccountListenerTest(
    @Autowired private val generationService: GenerationService,
    @Autowired private val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)
    private val account = members.newReader(jdbc)

    @AfterEach
    fun cleanUp() {
        jdbc.sql("delete from generation_job where account_id = :account").param("account", account).update()
    }

    @Test
    fun `a disabled account's jobs are cancelled`() {
        val id =
            generationService.enqueue(
                NewJob(
                    accountId = account,
                    kind = JobKind.PLAN,
                    dedupeKey = "plan:${UUID.randomUUID()}",
                    payload = mapOf(),
                ),
            )

        transactionTemplate.executeWithoutResult {
            jdbc
                .sql(
                    "update account set status = 'disabled', disabled_at = now() where id = :id",
                ).param("id", account)
                .update()
            applicationEventPublisher.publishEvent(
                AccountDisabled(
                    eventId = UUID.randomUUID(),
                    occurredAt = clock.instant(),
                    accountId = account,
                    operatorId = UUID.randomUUID(),
                ),
            )
        }

        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        await().atMost(Duration.ofSeconds(10)).until {
            jdbc
                .sql("select state from generation_job where id = :id")
                .param("id", id)
                .query(String::class.java)
                .single() == "cancelled"
        }
    }
}
