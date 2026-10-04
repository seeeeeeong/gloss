package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class HttpSessionsSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `an expired session's attributes are deleted with it`() {
        // Spring Session's cleanup deletes only from spring_session and relies on the cascade.
        val id = UUID.randomUUID().toString()
        jdbc
            .sql(
                """
                insert into spring_session (primary_id, session_id, creation_time, last_access_time,
                                            max_inactive_interval, expiry_time)
                values (:id, :sessionId, 0, 0, 60, 0)
                """.trimIndent(),
            ).param("id", id)
            .param("sessionId", UUID.randomUUID().toString())
            .update()
        jdbc
            .sql("insert into spring_session_attributes values (:id, 'SPRING_SECURITY_CONTEXT', '\\x00'::bytea)")
            .param("id", id)
            .update()

        jdbc.sql("delete from spring_session where primary_id = :id").param("id", id).update()

        val left =
            jdbc
                .sql("select count(*) from spring_session_attributes where session_primary_id = :id")
                .param("id", id)
                .query(Int::class.java)
                .single()
        assertThat(left).isZero()
    }
}
