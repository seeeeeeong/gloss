package gloss.domain.setup.domain.repository

import gloss.domain.setup.domain.SetupSession
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SetupSessionRepository : JpaRepository<SetupSession, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): SetupSession?

    // Held until commit, so two steps of one session take their turns and number their messages in order.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SetupSession s where s.id = :id and s.accountId = :accountId")
    fun findForUpdate(
        id: UUID,
        accountId: UUID,
    ): SetupSession?
}
