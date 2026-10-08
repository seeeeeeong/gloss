package gloss.generation.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Runs [JobReaper] through its proxy, so each round is its own transaction. */
@Component
class JobReaperJob(
    private val jobReaper: JobReaper,
) {
    @Scheduled(
        fixedDelayString = "\${gloss.generation.reap-every}",
        initialDelayString = "\${gloss.generation.reap-first-after}",
    )
    fun run() {
        jobReaper.reap()
    }
}
