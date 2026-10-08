package gloss.generation.application

import gloss.account.application.AccountService
import gloss.common.error.GlossException
import gloss.generation.domain.job.NewJob
import gloss.generation.infrastructure.JdbcJobQueue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.util.UUID

@Service
class GenerationService(
    private val accountService: AccountService,
    private val jdbcJobQueue: JdbcJobQueue,
    private val jobWorker: JobWorker,
    private val clock: Clock,
) {
    /**
     * Joins the caller's transaction, so the job exists only if the caller's work commits. The
     * worker is woken after the commit: woken before, it would find nothing to claim.
     */
    @Transactional
    fun enqueue(job: NewJob): UUID {
        jdbcJobQueue.lockAccount(job.accountId)
        // Read under the lock: a disable committed after this waits for it, and cancels what it adds.
        if (!accountService.isActive(job.accountId)) throw GlossException(GenerationError.ACCOUNT_DISABLED)
        val id = jdbcJobQueue.enqueue(job, clock.instant())
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = jobWorker.wake()
            },
        )
        return id
    }

    @Transactional
    fun cancelForDisabledAccount(accountId: UUID) {
        jdbcJobQueue.lockAccount(accountId)
        // A late event for a reader enabled again since: the jobs added after it are theirs to keep.
        if (accountService.isActive(accountId)) return
        jdbcJobQueue.cancelAll(accountId, clock.instant())
    }
}
