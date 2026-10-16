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
class MoveEventTypesSchemaTest(
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

    private fun migrate() {
        jdbc
            .sql(ClassPathResource("db/migration/V14__move_event_types.sql").getContentAsString(Charsets.UTF_8))
            .update()
    }

    @Test
    fun `an event waiting under its old class name is found again under the new one, in every module`() {
        val waiting =
            mapOf(
                "gloss.account.domain.event.AccountCreated" to "gloss.domain.account.domain.event.AccountCreated",
                "gloss.apikey.domain.event.ApiKeySaved" to "gloss.domain.apikey.domain.event.ApiKeySaved",
                "gloss.book.domain.event.TocConfirmed" to "gloss.domain.book.domain.event.TocConfirmed",
                "gloss.plan.domain.event.PlanKept" to "gloss.domain.plan.domain.event.PlanKept",
            ).map { (old, new) -> publish(old) to new }

        migrate()

        assertThat(waiting.map { (id, _) -> typeOf(id) }).containsExactlyElementsOf(waiting.map { it.second })
    }

    @Test
    fun `an event already under the new name, or outside the modules, is left alone`() {
        val moved = publish("gloss.domain.account.domain.event.AccountCreated")
        val shared = publish("gloss.common.Event")
        val accounting = publish("gloss.accounting.Event")

        migrate()

        assertThat(typeOf(moved)).isEqualTo("gloss.domain.account.domain.event.AccountCreated")
        assertThat(typeOf(shared)).isEqualTo("gloss.common.Event")
        assertThat(typeOf(accounting)).isEqualTo("gloss.accounting.Event")
    }
}
