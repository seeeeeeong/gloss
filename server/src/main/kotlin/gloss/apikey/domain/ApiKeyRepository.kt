package gloss.apikey.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByAccountIdAndProviderAndRevokedAtIsNull(
        accountId: UUID,
        provider: KeyProvider,
    ): ApiKey?

    // Held until commit, so two saves at once replace in turn instead of both finding no key in use.
    @Query(
        "select 1 from pg_advisory_xact_lock(hashtextextended('apikey:' || cast(:accountId as text), 0))",
        nativeQuery = true,
    )
    fun lockAccount(accountId: UUID): Int
}
