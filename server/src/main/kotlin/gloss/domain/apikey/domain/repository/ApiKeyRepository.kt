package gloss.domain.apikey.domain.repository

import gloss.domain.apikey.domain.ApiKey
import gloss.domain.apikey.domain.KeyProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
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

    // A bulk update leaves the version alone, so a use never collides with a save or a revoke.
    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :at where k.id = :id")
    fun recordUse(
        id: UUID,
        at: Instant,
    ): Int
}
