package gloss.apikey.application

import gloss.TestcontainersConfiguration
import gloss.apikey.FakeKeyCheckGateway
import gloss.apikey.application.dto.ApiKeySaveRequest
import gloss.apikey.domain.KeyCheckResult
import gloss.apikey.domain.KeyCipher
import gloss.apikey.domain.event.ApiKeyRevoked
import gloss.apikey.domain.event.ApiKeySaved
import gloss.apikey.domain.event.KeyRevocation
import gloss.common.error.CommonError
import gloss.common.error.ErrorCode
import gloss.common.error.GlossException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeKeyCheckGateway.Config::class)
@Transactional
@RecordApplicationEvents
class ApiKeyServiceTest(
    @Autowired private val apiKeyService: ApiKeyService,
    @Autowired private val fakeKeyCheckGateway: FakeKeyCheckGateway,
    @Autowired private val keyCipher: KeyCipher,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val entityManager: EntityManager,
) {
    // Not a real key: the fake gateway accepts anything well formed.
    private val typed = "sk-0123456789abcdef"

    private inline fun <reified T : Any> ApplicationEvents.published(): List<T> = stream(T::class.java).toList()

    @BeforeEach
    fun reset() {
        fakeKeyCheckGateway.reset()
    }

    // Rolled back with the test; no one signs in, so the hash is never read.
    private fun accountId(disabled: Boolean = false): UUID {
        val id = UUID.randomUUID()
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at, disabled_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', :status, false, 0, now(),
                        case when :disabled then now() end)
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "k-" + id.toString().take(12))
            .param("status", if (disabled) "disabled" else "active")
            .param("disabled", disabled)
            .update()
        return id
    }

    private fun assertFails(
        error: ErrorCode,
        call: () -> Unit,
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GlossException::class.java) {
            assertThat(it.error).isEqualTo(error)
        }
    }

    private data class Row(
        val status: String,
        val ciphertext: ByteArray?,
        val nonce: ByteArray?,
        val keyVersion: Int,
    )

    private fun rows(accountId: UUID): List<Row> {
        entityManager.flush()
        return jdbc
            .sql(
                """
                select status, ciphertext, nonce, key_version from api_key
                where account_id = :id order by created_at, id
                """.trimIndent(),
            ).param("id", accountId)
            .query { rs, _ ->
                Row(
                    status = rs.getString(1),
                    ciphertext = rs.getBytes(2),
                    nonce = rs.getBytes(3),
                    keyVersion = rs.getInt(4),
                )
            }.list()
    }

    @Test
    fun `a checked key is stored sealed, and only its last four come back`() {
        val account = accountId()

        val saved = apiKeyService.save(account, ApiKeySaveRequest("  $typed  "))

        assertThat(saved.last4).isEqualTo("cdef")
        assertThat(saved.status).isEqualTo("valid")
        assertThat(fakeKeyCheckGateway.checked).containsExactly(typed)
        val row = rows(account).single()
        assertThat(String(row.ciphertext!!)).doesNotContain(typed)
        val opened = keyCipher.open(KeyCipher.Sealed(row.ciphertext, row.nonce!!, row.keyVersion), account, "deepseek")
        assertThat(opened).isEqualTo(typed)
    }

    @Test
    fun `an account disabled while its key was being checked gets no key`() {
        val account = accountId(disabled = true)

        assertFails(CommonError.UNAUTHORIZED) { apiKeyService.save(account, ApiKeySaveRequest(typed)) }
        assertThat(rows(account)).isEmpty()
    }

    @Test
    fun `a malformed key is refused before it is sent anywhere`() {
        assertFails(ApiKeyError.INVALID_KEY) { apiKeyService.save(accountId(), ApiKeySaveRequest("sk 123")) }
        assertThat(fakeKeyCheckGateway.checked).isEmpty()
    }

    @Test
    fun `a key DeepSeek refuses is not stored`() {
        val account = accountId()
        fakeKeyCheckGateway.next = KeyCheckResult.Rejected

        assertFails(ApiKeyError.KEY_REJECTED) { apiKeyService.save(account, ApiKeySaveRequest(typed)) }
        assertThat(rows(account)).isEmpty()
    }

    @Test
    fun `a key that cannot be checked now is not stored`() {
        val account = accountId()
        fakeKeyCheckGateway.next = KeyCheckResult.Unavailable

        assertFails(ApiKeyError.KEY_CHECK_UNAVAILABLE) { apiKeyService.save(account, ApiKeySaveRequest(typed)) }
        assertThat(rows(account)).isEmpty()
    }

    @Test
    fun `a new key replaces the one in use, whose ciphertext is cleared`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()
        apiKeyService.save(account, ApiKeySaveRequest(typed))

        apiKeyService.save(account, ApiKeySaveRequest("sk-fedcba9876543210"))

        assertThat(rows(account).map { it.status to (it.ciphertext == null) })
            .containsExactly("revoked" to true, "valid" to false)
        assertThat(apiKeyService.current(account).last4).isEqualTo("3210")
        assertThat(events.published<ApiKeySaved>()).hasSize(2)
        assertThat(events.published<ApiKeyRevoked>().single().reason).isEqualTo(KeyRevocation.REPLACED)
    }

    @Test
    fun `a revoked key is gone, and revoking again changes nothing`(
        @Autowired events: ApplicationEvents,
    ) {
        val account = accountId()
        apiKeyService.save(account, ApiKeySaveRequest(typed))

        apiKeyService.revoke(account)
        apiKeyService.revoke(account)

        assertFails(ApiKeyError.NOT_REGISTERED) { apiKeyService.current(account) }
        assertThat(rows(account).single().ciphertext).isNull()
        assertThat(events.published<ApiKeyRevoked>().map { it.reason }).containsExactly(KeyRevocation.READER)
    }
}
