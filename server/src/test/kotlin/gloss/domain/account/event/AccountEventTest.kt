package gloss.domain.account.event

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.events.core.EventSerializer
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AccountEventTest(
    @Autowired private val eventSerializer: EventSerializer,
) {
    @ParameterizedTest
    @MethodSource("events")
    fun `an event left undelivered reads back from the registry as it was`(event: AccountEvent) {
        val stored = eventSerializer.serialize(event)

        assertThat(eventSerializer.deserialize(stored, event.javaClass)).isEqualTo(event)
    }

    private companion object {
        private val at = Instant.parse("2026-10-05T00:00:00Z")

        private fun id() = UUID.randomUUID()

        @JvmStatic
        fun events(): List<AccountEvent> =
            listOf(
                SignedIn(eventId = id(), occurredAt = at, accountId = id(), ip = "2001:db8::1"),
                SignInFailed(
                    eventId = id(),
                    occurredAt = at,
                    accountId = null,
                    ip = "203.0.113.7",
                    reason = SignInFailure.UNKNOWN_LOGIN_ID,
                ),
                PasswordChanged(eventId = id(), occurredAt = at, accountId = id()),
                PasswordReset(eventId = id(), occurredAt = at, accountId = id(), operatorId = id()),
                AccountCreated(eventId = id(), occurredAt = at, accountId = id(), operatorId = null, role = "member"),
                AccountDisabled(eventId = id(), occurredAt = at, accountId = id(), operatorId = id()),
                AccountEnabled(eventId = id(), occurredAt = at, accountId = id(), operatorId = id()),
            )
    }
}
