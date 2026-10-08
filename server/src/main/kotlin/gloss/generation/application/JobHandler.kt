package gloss.generation.application

import gloss.generation.domain.job.JobKind

/**
 * Does one kind of job. [handle] runs outside any transaction and may call the model; what it
 * returns is written in the transaction that marks the job succeeded, and only while this attempt
 * still holds the job. A handler that cannot finish throws [JobFailed].
 */
interface JobHandler {
    val kind: JobKind

    fun handle(job: RunningJob): () -> Unit
}
