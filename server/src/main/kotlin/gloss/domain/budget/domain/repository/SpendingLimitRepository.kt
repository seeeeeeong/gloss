package gloss.domain.budget.domain.repository

import gloss.domain.budget.domain.SpendingLimit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpendingLimitRepository : JpaRepository<SpendingLimit, UUID>
