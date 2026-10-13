package gloss.generation

import gloss.generation.application.JobHandler
import gloss.generation.application.RunningJob
import gloss.generation.domain.job.JobKind
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/** A handler for the one kind that has none of its own, so no real one runs beside it; each test says what it does. */
class FakeJobHandler : JobHandler {
    override val kind = JobKind.CHECK

    @Volatile var behaviour: (RunningJob) -> () -> Unit = { {} }

    override fun handle(job: RunningJob): () -> Unit = behaviour(job)

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun fakeJobHandler() = FakeJobHandler()
    }
}
