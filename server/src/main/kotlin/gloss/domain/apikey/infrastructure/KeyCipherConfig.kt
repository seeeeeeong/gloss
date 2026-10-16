package gloss.domain.apikey.infrastructure

import gloss.domain.apikey.domain.KeyCipher
import gloss.domain.apikey.domain.MasterKeyParser
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KeyCipherConfig {
    /** Refuses to start without a master key, rather than store readers' keys in the clear. */
    @Bean
    fun keyCipher(
        @Value("\${gloss.api-key.master-keys:}") masterKeys: String,
    ): KeyCipher = KeyCipher(MasterKeyParser.parse(masterKeys))
}
