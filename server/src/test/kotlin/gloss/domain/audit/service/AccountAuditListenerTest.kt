package gloss.domain.audit.service

import gloss.TestcontainersConfiguration
import gloss.domain.account.Browser
import gloss.domain.account.Members
import gloss.domain.account.domain.event.SignInFailed
import gloss.domain.account.domain.event.SignInFailure
import gloss.domain.audit.infrastructure.JdbcAuditEvents
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@MockitoSpyBean(types = [JdbcAuditEvents::class])
class AccountAuditListenerTest(
    @LocalServerPort private val port: Int,
    @Autowired private val accountAuditListener: AccountAuditListener,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val jdbcAuditEvents: JdbcAuditEvents,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private companion object {
        // A delivery takes milliseconds; the margin is for a loaded CI runner, so only a lost event fails.
        val DELIVERY: Duration = Duration.ofSeconds(10)

        // Mockito's any() returns null, which a Kotlin non-null parameter refuses; the matcher is what counts.
        fun anyEntry(): JdbcAuditEvents.Entry =
            ArgumentMatchers.any(JdbcAuditEvents.Entry::class.java)
                ?: JdbcAuditEvents.Entry(
                    eventId = UUID.randomUUID(),
                    occurredAt = Instant.EPOCH,
                    actorId = null,
                    subjectId = null,
                    kind = "",
                    detail = emptyMap(),
                )
    }

    private data class Row(
        val kind: String,
        val actorId: UUID?,
        val subjectId: UUID?,
        val detail: String,
    )

    private fun rows(subjectId: UUID): List<Row> =
        jdbc
            .sql("select kind, actor_id, subject_id, detail::text from audit_event where subject_id = :id order by id")
            .param("id", subjectId)
            .query { rs, _ ->
                Row(
                    kind = rs.getString(1),
                    actorId = rs.getObject(2, UUID::class.java),
                    subjectId = rs.getObject(3, UUID::class.java),
                    detail = rs.getString(4),
                )
            }.list()

    private fun accountId(loginId: String): UUID =
        jdbc
            .sql("select id from account where login_id = :loginId")
            .param("loginId", loginId)
            .query(UUID::class.java)
            .single()

    @Test
    fun `a failed sign-in is recorded with only the allowed fields`() {
        val subject = UUID.randomUUID()

        accountAuditListener.on(
            SignInFailed(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-10-05T00:00:00Z"),
                accountId = subject,
                ip = "203.0.113.7",
                reason = SignInFailure.WRONG_PASSWORD,
            ),
        )

        // The listener runs on its own thread, as it does after a commit.
        await().atMost(DELIVERY).untilAsserted {
            assertThat(rows(subject)).containsExactly(
                Row(
                    kind = "sign_in_failed",
                    actorId = null,
                    subjectId = subject,
                    detail = """{"ip": "203.0.113.7", "reason": "wrong_password"}""",
                ),
            )
        }
    }

    @Test
    fun `the record is written on another thread, so a slow audit never holds up a request`() {
        val writer = CompletableFuture<Thread>()
        doAnswer {
            writer.complete(Thread.currentThread())
            it.callRealMethod()
        }.`when`(jdbcAuditEvents).append(anyEntry())

        accountAuditListener.on(
            SignInFailed(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-10-05T00:00:00Z"),
                accountId = UUID.randomUUID(),
                ip = "203.0.113.7",
                reason = SignInFailure.WRONG_PASSWORD,
            ),
        )

        assertThat(writer.get(DELIVERY.seconds, TimeUnit.SECONDS)).isNotSameAs(Thread.currentThread())
    }

    @Test
    fun `a sign-in reaches the audit after it commits`() {
        val loginId = members.create()
        Browser(port).signIn(loginId, members.password)
        val id = accountId(loginId)

        await().atMost(DELIVERY).untilAsserted {
            assertThat(rows(id).map { it.kind to it.actorId }).containsExactly("signed_in" to id)
        }
    }
}
