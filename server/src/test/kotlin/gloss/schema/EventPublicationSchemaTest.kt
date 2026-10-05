package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class EventPublicationSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `the registry records, completes and removes a publication`() {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, status)
                values (:id, 'audit.account', 'gloss.Event', '{}', now(), 'PUBLISHED')
                """.trimIndent(),
            ).param("id", id)
            .update()

        assertThat(
            jdbc
                .sql("update event_publication set completion_date = now(), status = 'COMPLETED' where id = :id")
                .param("id", id)
                .update(),
        ).isEqualTo(1)
        assertThat(jdbc.sql("delete from event_publication where id = :id").param("id", id).update()).isEqualTo(1)
    }
}
