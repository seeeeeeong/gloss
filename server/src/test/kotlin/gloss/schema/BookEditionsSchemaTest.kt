package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class BookEditionsSchemaTest(
    @Autowired private val jdbc: JdbcClient,
) {
    private fun account(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "e-" + id.toString().take(12))
            .update()
        return id
    }

    private fun book(
        publishedOn: LocalDate?,
        kind: String?,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at, published_on, kind)
                values (:id, 0, :account, '오브젝트', '{}', 'manual', now(), :publishedOn, :kind)
                """.trimIndent(),
            ).param("id", id)
            .param("account", account())
            .param("publishedOn", publishedOn)
            .param("kind", kind)
            .update()
        return id
    }

    private fun assertRefused(insert: () -> Unit) {
        jdbc.sql("savepoint attempt").update()
        assertThatThrownBy(insert).isInstanceOf(DataIntegrityViolationException::class.java)
        jdbc.sql("rollback to savepoint attempt").update()
    }

    @Test
    fun `a book keeps when it came out and its kind, and a book without them is kept too`() {
        val dated = book(LocalDate.of(2019, 6, 17), "eBook")
        book(null, null)

        val kept =
            jdbc
                .sql("select published_on, kind from book where id = :id")
                .param("id", dated)
                .query { row, _ -> row.getObject(1, LocalDate::class.java) to row.getString(2) }
                .single()

        assertThat(kept).isEqualTo(LocalDate.of(2019, 6, 17) to "eBook")
    }

    @Test
    fun `a kind that is empty or longer than a short word is refused`() {
        assertRefused { book(null, "") }
        assertRefused { book(null, "가".repeat(21)) }
    }
}
