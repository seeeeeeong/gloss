package gloss.domain.generation.service

import gloss.domain.generation.domain.ClaimedJob
import gloss.domain.generation.domain.job.JobKind
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Does one kind of job. [handle] runs outside any transaction and may call the model; what it
 * returns is written in the transaction that marks the job succeeded, and only while this attempt
 * still holds the job. A handler that cannot finish throws [JobFailed].
 */
interface JobHandler {
    val kind: JobKind

    fun handle(job: RunningJob): () -> Unit
}

/** A claimed job while its handler runs. The heartbeat marks it cancelled or lost from another thread. */
class RunningJob(
    val claimed: ClaimedJob,
) {
    private val cancelRequested = AtomicBoolean(false)
    private val lost = AtomicBoolean(false)

    val jobId: UUID get() = claimed.lease.jobId

    val attempt: Int get() = claimed.lease.attempt

    val accountId: UUID get() = claimed.accountId

    val payload: Map<String, String> get() = claimed.payload

    val wasCancelled: Boolean get() = cancelRequested.get()

    val wasLost: Boolean get() = lost.get()

    /** For a model call's cancel check: true once nothing this attempt makes can be kept. */
    fun isCancelled(): Boolean = cancelRequested.get() || lost.get()

    fun requestCancel() = cancelRequested.set(true)

    fun lose() = lost.set(true)
}

/** A handler's failure. [errorKind] is kept on the job; [retryable] asks for another attempt, if one is left. */
class JobFailed(
    val errorKind: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : RuntimeException(errorKind, cause)
