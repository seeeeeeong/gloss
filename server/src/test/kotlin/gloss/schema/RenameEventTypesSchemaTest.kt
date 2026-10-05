package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class RenameEventTypesSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun publish(eventType: String): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, status)
                values (:id, 'audit.account', :type, '{}', now(), 'PUBLISHED')
                """.trimIndent(),
            ).param("id", id)
            .param("type", eventType)
            .update()
        return id
    }

    private fun typeOf(id: UUID): String =
        jdbc
            .sql("select event_type from event_publication where id = :id")
            .param("id", id)
            .query(String::class.java)
            .single()

    @Test
    fun `an event waiting under its old class name is found again under the new one`() {
        val waiting = publish("gloss.domain.account.event.AccountCreated")
        val other = publish("gloss.other.Event")

        jdbc
            .sql(
                ClassPathResource("db/migration/V6__rename_event_types.sql").getContentAsString(Charsets.UTF_8),
            ).update()

        assertThat(typeOf(waiting)).isEqualTo("gloss.account.domain.event.AccountCreated")
        assertThat(typeOf(other)).isEqualTo("gloss.other.Event")
    }
}
