package gloss.domain.apikey.domain.event

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
class ApiKeyEventTest(
    @Autowired private val eventSerializer: EventSerializer,
) {
    @ParameterizedTest
    @MethodSource("events")
    fun `an event left undelivered reads back from the registry as it was`(event: ApiKeyEvent) {
        val stored = eventSerializer.serialize(event)

        assertThat(eventSerializer.deserialize(stored, event.javaClass)).isEqualTo(event)
    }

    private companion object {
        private val at = Instant.parse("2026-10-06T00:00:00Z")

        @JvmStatic
        fun events(): List<ApiKeyEvent> =
            listOf(
                ApiKeySaved(
                    eventId = UUID.randomUUID(),
                    occurredAt = at,
                    accountId = UUID.randomUUID(),
                    provider = "deepseek",
                ),
                ApiKeyRevoked(
                    eventId = UUID.randomUUID(),
                    occurredAt = at,
                    accountId = UUID.randomUUID(),
                    provider = "deepseek",
                    reason = KeyRevocation.ACCOUNT_DISABLED,
                ),
            )
    }
}
