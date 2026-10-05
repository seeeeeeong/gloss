package gloss.audit.application

import gloss.TestcontainersConfiguration
import gloss.apikey.domain.event.ApiKeyRevoked
import gloss.apikey.domain.event.KeyRevocation
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ApiKeyAuditListenerTest(
    @Autowired private val apiKeyAuditListener: ApiKeyAuditListener,
    @Autowired private val jdbc: JdbcClient,
) {
    private companion object {
        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        val DELIVERY: Duration = Duration.ofSeconds(10)
    }

    @Test
    fun `a revoked key is recorded with the provider and why, never the key`() {
        val account = UUID.randomUUID()

        apiKeyAuditListener.on(
            ApiKeyRevoked(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-10-06T00:00:00Z"),
                accountId = account,
                provider = "deepseek",
                reason = KeyRevocation.ACCOUNT_DISABLED,
            ),
        )

        await().atMost(DELIVERY).untilAsserted {
            assertThat(
                jdbc
                    .sql(
                        """
                        select kind || ' ' || coalesce(actor_id::text, '-') || ' ' || detail::text
                        from audit_event where subject_id = :id
                        """.trimIndent(),
                    ).param("id", account)
                    .query(String::class.java)
                    .list(),
            ).containsExactly("""api_key_revoked - {"reason": "account_disabled", "provider": "deepseek"}""")
        }
    }
}
