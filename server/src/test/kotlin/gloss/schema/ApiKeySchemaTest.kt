package gloss.schema

import gloss.TestcontainersConfiguration
import gloss.domain.account.Members
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ApiKeySchemaTest(
    @Autowired private val jdbc: JdbcClient,
    @Autowired passwordEncoder: PasswordEncoder,
    @Autowired clock: Clock,
) {
    private val members = Members(jdbc, passwordEncoder, clock)

    private fun accountId(): UUID =
        jdbc
            .sql("select id from account where login_id = :loginId")
            .param("loginId", members.create())
            .query(UUID::class.java)
            .single()

    private fun insert(
        accountId: UUID,
        revoked: Boolean = false,
        nonce: ByteArray? = ByteArray(12),
    ) = jdbc
        .sql(
            """
            insert into api_key (id, version, account_id, provider, ciphertext, nonce, key_version, last4, status,
                                 validated_at, created_at, revoked_at)
            values (:id, 0, :accountId, 'deepseek', :ciphertext, :nonce, 1, 'cdef', :status, now(), now(),
                    case when :revoked then now() end)
            """.trimIndent(),
        ).param("id", UUID.randomUUID())
        .param("accountId", accountId)
        .param("ciphertext", if (revoked) null else ByteArray(24))
        .param("nonce", if (revoked) null else nonce)
        .param("status", if (revoked) "revoked" else "valid")
        .param("revoked", revoked)
        .update()

    @Test
    fun `an account has one key in use per provider, beside any it revoked`() {
        val account = accountId()
        insert(account, revoked = true)
        insert(account)

        assertThatThrownBy { insert(account) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a nonce is twelve bytes`() {
        assertThatThrownBy { insert(accountId(), nonce = ByteArray(16)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a revoked key keeps no ciphertext`() {
        assertThatCode { insert(accountId(), revoked = true) }.doesNotThrowAnyException()
    }

    @Test
    fun `a key in use keeps its ciphertext`() {
        val account = accountId()
        insert(account)

        assertThatThrownBy {
            jdbc
                .sql("update api_key set ciphertext = null where account_id = :id")
                .param("id", account)
                .update()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a revoked key keeps neither part of its seal, not even one`() {
        val account = accountId()
        insert(account)

        assertThatThrownBy {
            jdbc
                .sql(
                    """
                    update api_key set status = 'revoked', revoked_at = now(), nonce = null
                    where account_id = :id
                    """.trimIndent(),
                ).param("id", account)
                .update()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
