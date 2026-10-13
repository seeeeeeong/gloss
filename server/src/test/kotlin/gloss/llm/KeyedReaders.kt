package gloss.llm

import gloss.apikey.application.ApiKeyService
import gloss.apikey.application.dto.ApiKeySaveRequest
import gloss.budget.application.BudgetService
import gloss.budget.application.dto.LimitRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/**
 * Readers a model call can be made for: an active account, with a key and a monthly limit unless a
 * test leaves one out. Committed, as the services commit their own steps; [removeAll] after each test.
 */
class KeyedReaders(
    private val jdbc: JdbcClient,
    private val apiKeyService: ApiKeyService,
    private val budgetService: BudgetService,
) {
    private val created = mutableListOf<UUID>()

    fun create(
        key: Boolean = true,
        limitMicros: Long? = 1_000_000,
    ): UUID {
        val id = UUID.randomUUID()
        created += id
        jdbc
            .sql(
                """
                insert into account (id, version, login_id, display_name, password_hash, role, status,
                                     must_change_password, session_epoch, created_at)
                values (:id, 0, :loginId, 'Reader', 'unused', 'member', 'active', false, 0, now())
                """.trimIndent(),
            ).param("id", id)
            .param("loginId", "k-" + id.toString().take(12))
            .update()
        // Not a real key: the fake check accepts anything well formed.
        if (key) apiKeyService.save(id, ApiKeySaveRequest("sk-0123456789abcdef"))
        if (limitMicros != null) budgetService.setLimit(id, LimitRequest(limitMicros))
        return id
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun keyedReaders(
            jdbc: JdbcClient,
            apiKeyService: ApiKeyService,
            budgetService: BudgetService,
        ) = KeyedReaders(jdbc, apiKeyService, budgetService)
    }

    // The ledger is append-only and holds no key to an account, so its rows stay.
    fun removeAll() {
        created.forEach { id ->
            jdbc.sql("delete from api_key where account_id = :id").param("id", id).update()
            jdbc.sql("delete from spending_limit where account_id = :id").param("id", id).update()
            jdbc.sql("delete from account where id = :id").param("id", id).update()
        }
        created.clear()
    }
}
