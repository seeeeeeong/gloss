package gloss

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer(POSTGRES_IMAGE)

    private companion object {
        // Keep in step with compose.yaml.
        const val POSTGRES_IMAGE = "postgres:18-alpine"
    }
}
