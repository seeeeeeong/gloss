package gloss.budget.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpendingLimitRepository : JpaRepository<SpendingLimit, UUID>
