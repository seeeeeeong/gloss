package gloss.generation.application

import gloss.account.application.AccountService
import gloss.common.util.traceOf
import gloss.generation.domain.JobEnding
import gloss.generation.domain.RetryDecision
import gloss.generation.domain.RetryPolicy
import gloss.generation.infrastructure.JdbcJobQueue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import kotlin.random.Random

/** Runs one claimed job to its end: succeeded with its writes, retried, failed or cancelled. */
@Component
class JobRunner(
    jobHandlers: ObjectProvider<JobHandler>,
    private val jdbcJobQueue: JdbcJobQueue,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
    private val accountService: AccountService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // None until each kind's pull request brings its handler; a job of a kind without one fails.
    private val handlers = jobHandlers.orderedStream().toList().associateBy { it.kind }

    fun run(job: RunningJob) {
        // Disabled before the job started, perhaps before its cancel was delivered: nothing may run on
        // the reader's key. A disable once it runs reaches it at the next heartbeat.
        if (!accountService.isActive(job.claimed.accountId)) return end(job, JobEnding.Cancelled)
        val handler = handlers[job.claimed.kind] ?: return end(job, JobEnding.Failed(NO_HANDLER))
        val write =
            try {
                handler.handle(job)
            } catch (e: JobFailed) {
                return failed(job, e)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                log.warn("Job {} failed{}", job.claimed.lease.jobId, traceOf(e))
                return failed(job, JobFailed(ERROR, retryable = false, cause = e))
            }
        when {
            // The reaper has handed the job on; what this attempt made is dropped.
            job.wasLost -> return

            job.wasCancelled -> end(job, JobEnding.Cancelled)

            else -> succeed(job, write)
        }
    }

    private fun succeed(
        job: RunningJob,
        write: () -> Unit,
    ) {
        try {
            transactionTemplate.executeWithoutResult {
                val ended = jdbcJobQueue.finish(job.claimed.lease, JobEnding.Succeeded, clock.instant())
                if (ended == JobEnding.Succeeded) write()
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Rolled back, so the job is still running; ended here rather than left to be reaped and run again.
            log.warn("Job {} could not be saved{}", job.claimed.lease.jobId, traceOf(e))
            end(job, JobEnding.Failed(WRITE_FAILED))
        }
    }

    private fun failed(
        job: RunningJob,
        failure: JobFailed,
    ) {
        if (job.wasLost) return
        if (job.wasCancelled) return end(job, JobEnding.Cancelled)
        val claimed = job.claimed
        val decision =
            RetryPolicy.decide(
                attempt = claimed.lease.attempt,
                maxAttempts = claimed.maxAttempts,
                retryable = failure.retryable,
                jitter = Random.nextDouble(),
            )
        when (decision) {
            is RetryDecision.Retry -> {
                jdbcJobQueue.retry(
                    lease = claimed.lease,
                    errorKind = failure.errorKind,
                    delay = decision.after,
                    now = clock.instant(),
                )
            }

            RetryDecision.Fail -> {
                end(job, JobEnding.Failed(failure.errorKind))
            }
        }
    }

    private fun end(
        job: RunningJob,
        ending: JobEnding,
    ) {
        jdbcJobQueue.finish(job.claimed.lease, ending, clock.instant())
    }

    private companion object {
        const val NO_HANDLER = "no_handler"
        const val ERROR = "error"
        const val WRITE_FAILED = "write_failed"
    }
}
