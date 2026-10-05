package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class AuditSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun insert(
        eventId: UUID = UUID.randomUUID(),
        detail: String = """{"ip": "203.0.113.7"}""",
    ) = jdbc
        .sql(
            """
            insert into audit_event (event_id, occurred_at, kind, detail)
            values (:eventId, now(), 'sign_in_failed', cast(:detail as jsonb))
            """.trimIndent(),
        ).param("eventId", eventId)
        .param("detail", detail)
        .update()

    @Test
    fun `an event without an actor or subject is recorded`() {
        assertThatCode { insert() }.doesNotThrowAnyException()
    }

    @Test
    fun `the detail is an object, never a bare value or a list`() {
        assertThatThrownBy { insert(detail = """["203.0.113.7"]""") }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `an event delivered twice is recorded once`() {
        val eventId = UUID.randomUUID()
        insert(eventId)

        assertThatThrownBy { insert(eventId) }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `a recorded event cannot be changed`() {
        insert()

        assertThatThrownBy { jdbc.sql("update audit_event set kind = 'other'").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }

    @Test
    fun `a recorded event cannot be deleted`() {
        insert()

        assertThatThrownBy { jdbc.sql("delete from audit_event").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }
}
