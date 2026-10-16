package gloss.domain.llm

import gloss.domain.llm.domain.ChatRequest
import gloss.domain.llm.domain.ChatResult
import gloss.domain.llm.domain.LlmGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Plays a provider as a test scripts it, and remembers what it was asked. */
class FakeLlmGateway : LlmGateway {
    var answer: (onText: (String) -> Unit, isCancelled: () -> Boolean) -> ChatResult = { _, _ -> error("not scripted") }
    val requests = mutableListOf<ChatRequest>()
    val secrets = mutableListOf<String>()

    override fun stream(
        secret: String,
        request: ChatRequest,
        onText: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): ChatResult {
        requests += request
        secrets += secret
        return answer(onText, isCancelled)
    }

    fun reset() {
        answer = { _, _ -> error("not scripted") }
        requests.clear()
        secrets.clear()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakeLlmGateway() = FakeLlmGateway()
    }
}
