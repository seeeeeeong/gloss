package gloss.domain.generation.service

import gloss.common.error.GlossException
import gloss.domain.account.service.AccountService
import gloss.domain.generation.domain.job.JobStatus
import gloss.domain.generation.domain.job.NewJob
import gloss.domain.generation.infrastructure.JdbcJobQueue
import gloss.domain.generation.infrastructure.JdbcJobStatuses
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
    private val jdbcJobStatuses: JdbcJobStatuses,
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

    /** How the newest job for a piece of work stands; null when it was never asked for. */
    @Transactional(readOnly = true)
    fun latest(
        accountId: UUID,
        dedupeKey: String,
    ): JobStatus? = jdbcJobStatuses.latest(accountId, dedupeKey)

    @Transactional
    fun cancelForDisabledAccount(accountId: UUID) {
        jdbcJobQueue.lockAccount(accountId)
        // A late event for a reader enabled again since: the jobs added after it are theirs to keep.
        if (accountService.isActive(accountId)) return
        jdbcJobQueue.cancelAll(accountId, clock.instant())
    }
}
