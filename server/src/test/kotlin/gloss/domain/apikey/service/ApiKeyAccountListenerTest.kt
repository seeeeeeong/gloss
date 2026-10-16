package gloss.domain.apikey.service

import gloss.TestcontainersConfiguration
import gloss.domain.account.Members
import gloss.domain.account.domain.event.AccountDisabled
import gloss.domain.apikey.FakeKeyCheckGateway
import gloss.domain.apikey.dto.request.ApiKeySaveRequest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeKeyCheckGateway.Config::class)
class ApiKeyAccountListenerTest(
    @Autowired private val apiKeyService: ApiKeyService,
    @Autowired private val applicationEventPublisher: ApplicationEventPublisher,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private companion object {
        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        val DELIVERY: Duration = Duration.ofSeconds(10)
    }

    private fun accountWithKey(): Pair<String, UUID> {
        val loginId = members.create()
        val account =
            jdbc
                .sql("select id from account where login_id = :loginId")
                .param("loginId", loginId)
                .query(UUID::class.java)
                .single()
        apiKeyService.save(account, ApiKeySaveRequest("sk-0123456789abcdef"))
        return loginId to account
    }

    private fun statuses(account: UUID): List<String?> =
        jdbc
            .sql("select status from api_key where account_id = :id")
            .param("id", account)
            .query(String::class.java)
            .list()

    /** Published and committed as the account module does, then waited on until every listener has it. */
    private fun deliverDisabled(
        account: UUID,
        at: Instant,
    ) {
        val eventId = UUID.randomUUID()
        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(
                AccountDisabled(
                    eventId = eventId,
                    occurredAt = at,
                    accountId = account,
                    operatorId = UUID.randomUUID(),
                ),
            )
        }
        await().atMost(DELIVERY).untilAsserted {
            assertThat(
                jdbc
                    .sql("select count(*) from event_publication where serialized_event like :id")
                    .param("id", "%$eventId%")
                    .query(Long::class.java)
                    .single(),
            ).isZero()
        }
    }

    @Test
    fun `a disabled account's key is revoked`() {
        val (loginId, account) = accountWithKey()
        members.disable(loginId)

        deliverDisabled(account, clock.instant())

        assertThat(statuses(account)).containsExactly("revoked")
    }

    @Test
    fun `an account enabled again before the event arrives keeps its key`() {
        val (_, account) = accountWithKey()

        deliverDisabled(account, clock.instant())

        assertThat(statuses(account)).containsExactly("valid")
    }
}
