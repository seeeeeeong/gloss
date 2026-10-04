package gloss

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The database every Spring test runs against: the same major version as
 * production, started once per test JVM and wired in through @ServiceConnection.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer(POSTGRES_IMAGE)

    private companion object {
        /** The version compose.yaml and the deployment run. A test on another version proves less. */
        const val POSTGRES_IMAGE = "postgres:18-alpine"
    }
}
