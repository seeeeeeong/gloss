package gloss.generation.application

import gloss.common.util.UuidV7
import gloss.common.util.traceOf
import gloss.generation.domain.ClaimedJob
import gloss.generation.infrastructure.JdbcJobQueue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Claims due jobs while a slot is free and runs each on its own thread, renewing every running
 * job's lease. Woken after an enqueue commits; otherwise it looks again every [POLL]. On
 * shutdown it stops claiming and waits for its jobs, whose model calls are already billing.
 */
@Component
class JobWorker(
    private val jdbcJobQueue: JdbcJobQueue,
    private val jobRunner: JobRunner,
    private val clock: Clock,
    @Value("\${gloss.generation.concurrency}") private val concurrency: Int,
    @Value("\${gloss.generation.heartbeat-every}") private val heartbeatEvery: Duration,
    @Value("\${gloss.generation.autostart}") private val autostart: Boolean,
) : SmartLifecycle {
    // A lease outlives three missed heartbeats, so only a worker that is gone loses its job.
    private val lease = heartbeatEvery.multipliedBy(LEASE_BEATS)
    private val log = LoggerFactory.getLogger(javaClass)
    private val owner = "worker-${UuidV7.at(clock.instant())}"
    private val wakes = Semaphore(0)
    private val slots = Semaphore(concurrency)
    private val running = ConcurrentHashMap<UUID, RunningJob>()

    @Volatile private var active = false
    private var claimLoop: Thread? = null
    private var heartbeats: ScheduledExecutorService? = null

    /** Looks for a due job now rather than at the next poll. */
    fun wake() {
        if (wakes.availablePermits() == 0) wakes.release()
    }

    override fun isAutoStartup() = autostart

    override fun isRunning() = active

    override fun start() {
        active = true
        heartbeats =
            Executors
                .newSingleThreadScheduledExecutor {
                    Thread(
                        it,
                        "generation-heartbeat",
                    ).apply { isDaemon = true }
                }.apply {
                    val every = heartbeatEvery.toMillis()
                    scheduleWithFixedDelay(::heartbeat, every, every, TimeUnit.MILLISECONDS)
                }
        claimLoop =
            Thread
                .ofPlatform()
                .name("generation-claim")
                .daemon()
                .start(::claimWhileActive)
    }

    override fun stop(callback: Runnable) {
        active = false
        claimLoop?.interrupt()
        Thread.ofVirtual().name("generation-drain").start {
            // Every slot back means every job has ended; the leases are renewed until then.
            slots.acquireUninterruptibly(concurrency)
            slots.release(concurrency)
            heartbeats?.shutdownNow()
            callback.run()
        }
    }

    override fun stop() = stop {}

    private fun claimWhileActive() {
        try {
            while (active) {
                slots.acquire()
                // A stop that came while waiting for the slot: a job claimed now would hold the shutdown.
                val claimed = if (active) claimOrNull() else null
                if (claimed != null) {
                    launch(claimed)
                } else {
                    slots.release()
                    wakes.tryAcquire(POLL.toMillis(), TimeUnit.MILLISECONDS)
                    wakes.drainPermits()
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun claimOrNull(): ClaimedJob? =
        try {
            val now = clock.instant()
            jdbcJobQueue.claim(owner = owner, now = now, lease = lease)
        } catch (e: RuntimeException) {
            log.warn("Could not claim a job{}", traceOf(e))
            null
        }

    private fun launch(claimed: ClaimedJob) {
        val job = RunningJob(claimed)
        running[claimed.lease.jobId] = job
        Thread.ofVirtual().name("generation-job").start {
            try {
                jobRunner.run(job)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Left running; its lease runs out and the reaper hands it on.
                log.warn("Job {} could not be ended{}", claimed.lease.jobId, traceOf(e))
            } finally {
                // Only this attempt's entry: the reaper may have handed the job to a newer one here.
                running.remove(claimed.lease.jobId, job)
                slots.release()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun heartbeat() {
        running.values.forEach { job ->
            try {
                when (jdbcJobQueue.heartbeat(job.claimed.lease, lease)) {
                    null -> job.lose()
                    true -> job.requestCancel()
                    false -> Unit
                }
            } catch (e: RuntimeException) {
                // A missed beat is not a lost lease; the next one may land well before it runs out.
                log.warn("Could not renew the lease of job {}{}", job.claimed.lease.jobId, traceOf(e))
            }
        }
    }

    private companion object {
        const val LEASE_BEATS = 4L

        // Only for a wake-up that never came, as from a job another instance enqueued or a retry come due:
        // a few seconds is nothing beside a model call, and an idle instance asks once in a while.
        val POLL: Duration = Duration.ofSeconds(5)
    }
}
