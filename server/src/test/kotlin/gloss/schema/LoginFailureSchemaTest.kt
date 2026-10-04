package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class LoginFailureSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun insert(ip: String = "203.0.113.7") =
        jdbc
            .sql(
                """
                insert into login_failure (login_id_key, ip, attempted_at)
                values ('reader', cast(:ip as inet), now())
                """.trimIndent(),
            ).param("ip", ip)
            .update()

    @Test
    fun `a failure is recorded with an IPv4 or IPv6 address`() {
        assertThatCode { insert("203.0.113.7") }.doesNotThrowAnyException()
        assertThatCode { insert("2001:db8::1") }.doesNotThrowAnyException()
    }

    @Test
    fun `a recorded failure cannot be changed`() {
        insert()

        assertThatThrownBy { jdbc.sql("update login_failure set login_id_key = 'other'").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }

    @Test
    fun `a recorded failure cannot be deleted`() {
        insert()

        assertThatThrownBy { jdbc.sql("delete from login_failure").update() }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")
    }
}
