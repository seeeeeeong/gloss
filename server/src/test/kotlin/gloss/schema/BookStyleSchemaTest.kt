package gloss.schema

import gloss.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class BookStyleSchemaTest(
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
            .param("loginId", "s-" + id.toString().take(12))
            .update()
        return id
    }

    // A book as one was added before the style: no level and no voice named.
    private fun bookAsBefore(): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at)
                values (:id, 0, :account, '오브젝트', '{}', 'manual', now())
                """.trimIndent(),
            ).param("id", id)
            .param("account", account())
            .update()
        return id
    }

    private fun book(
        level: String,
        voice: String,
    ) {
        jdbc
            .sql(
                """
                insert into book (id, version, account_id, title, authors, source, created_at, level, voice)
                values (:id, 0, :account, '오브젝트', '{}', 'manual', now(), :level, :voice)
                """.trimIndent(),
            ).param("id", UUID.randomUUID())
            .param("account", account())
            .param("level", level)
            .param("voice", voice)
            .update()
    }

    // PostgreSQL ends a transaction at its first error; a savepoint lets one test try several rows.
    private fun assertRefused(insert: () -> Unit) {
        jdbc.sql("savepoint attempt").update()
        assertThatThrownBy(insert).isInstanceOf(DataIntegrityViolationException::class.java)
        jdbc.sql("rollback to savepoint attempt").update()
    }

    @Test
    fun `a book from before the choice is written for course readers in the textbook voice`() {
        val id = bookAsBefore()

        val style =
            jdbc
                .sql("select level || '/' || voice from book where id = :id")
                .param("id", id)
                .query(String::class.java)
                .single()

        assertThat(style).isEqualTo("course/textbook")
    }

    @Test
    fun `every level and voice offered is kept`() {
        listOf("intro", "course", "work").forEach { level ->
            listOf("textbook", "explain", "note").forEach { voice ->
                assertThatCode { book(level, voice) }.doesNotThrowAnyException()
            }
        }
    }

    @Test
    fun `a level or voice not offered is refused, and so is none`() {
        assertRefused { book("expert", "textbook") }
        assertRefused { book("course", "poem") }
        assertRefused { book("Course", "textbook") }
        assertRefused {
            jdbc
                .sql("update book set voice = null where id = :id")
                .param("id", bookAsBefore())
                .update()
        }
    }
}
