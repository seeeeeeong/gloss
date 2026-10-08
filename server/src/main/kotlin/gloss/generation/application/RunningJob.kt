package gloss.generation.application

import gloss.generation.domain.ClaimedJob
import java.util.concurrent.atomic.AtomicBoolean

/** A claimed job while its handler runs. The heartbeat marks it cancelled or lost from another thread. */
class RunningJob(
    val claimed: ClaimedJob,
) {
    private val cancelRequested = AtomicBoolean(false)
    private val lost = AtomicBoolean(false)

    val wasCancelled: Boolean get() = cancelRequested.get()

    val wasLost: Boolean get() = lost.get()

    /** For a model call's cancel check: true once nothing this attempt makes can be kept. */
    fun isCancelled(): Boolean = cancelRequested.get() || lost.get()

    fun requestCancel() = cancelRequested.set(true)

    fun lose() = lost.set(true)
}
