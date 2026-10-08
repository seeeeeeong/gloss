package gloss.generation.application

import gloss.generation.infrastructure.JdbcJobReap
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class JobReaper(
    private val jdbcJobReap: JdbcJobReap,
    private val clock: Clock,
) {
    /** Hands on the jobs whose worker died: their lease ran out with nobody renewing it. */
    @Transactional
    fun reap(): Int {
        if (!jdbcJobReap.tryLockReap()) return 0
        return jdbcJobReap.reap(clock.instant(), REAP_BATCH)
    }

    private companion object {
        // One round's share, in one short transaction; a dead worker leaves a few, and the next round takes any rest.
        const val REAP_BATCH = 100
    }
}
