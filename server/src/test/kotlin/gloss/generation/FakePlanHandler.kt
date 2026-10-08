package gloss.generation

import gloss.generation.application.JobHandler
import gloss.generation.application.RunningJob
import gloss.generation.domain.job.JobKind
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/** Stands in for the plan handler; each test says what it does. */
class FakePlanHandler : JobHandler {
    override val kind = JobKind.PLAN

    @Volatile var behaviour: (RunningJob) -> () -> Unit = { {} }

    override fun handle(job: RunningJob): () -> Unit = behaviour(job)

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun fakePlanHandler() = FakePlanHandler()
    }
}
