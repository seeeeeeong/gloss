package gloss.domain.account.repository

import gloss.domain.account.entity.Account
import gloss.domain.account.entity.AccountRole
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByLoginId(loginId: String): Account?

    // A second change from the same password waits, then sees the new hash and is refused.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    fun findForUpdate(id: UUID): Account?

    fun existsByRole(role: AccountRole): Boolean

    // Held until commit, so a second instance starting at the same time waits and then sees the first operator.
    @Query("select 1 from pg_advisory_xact_lock(hashtext('gloss.first-operator'))", nativeQuery = true)
    fun lockFirstOperatorCreation(): Int

    // A bulk update leaves the version alone, so two sign-ins at once do not fail each other.
    @Modifying
    @Query("update Account a set a.lastLoginAt = :at where a.id = :id")
    fun recordLogin(
        id: UUID,
        at: Instant,
    ): Int
}
