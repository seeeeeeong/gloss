package gloss.domain.apikey

import gloss.domain.apikey.domain.ApiKeySecret
import gloss.domain.apikey.domain.KeyCheckGateway
import gloss.domain.apikey.domain.KeyCheckResult
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Answers what a test sets instead of calling DeepSeek, and remembers what it was asked. */
class FakeKeyCheckGateway : KeyCheckGateway {
    var next: KeyCheckResult = KeyCheckResult.Valid
    val checked = mutableListOf<String>()

    override fun check(secret: ApiKeySecret): KeyCheckResult {
        checked += secret.value
        return next
    }

    fun reset() {
        next = KeyCheckResult.Valid
        checked.clear()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun fakeKeyCheckGateway() = FakeKeyCheckGateway()
    }
}
