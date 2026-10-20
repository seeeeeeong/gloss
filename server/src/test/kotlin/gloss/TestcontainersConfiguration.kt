package gloss

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    // Each context gets a database of its own in the one container: tests that commit must not see
    // another context's rows.
    @Bean
    fun postgresProperties(): DynamicPropertyRegistrar {
        val url = createDatabase()
        return DynamicPropertyRegistrar { registry ->
            registry.add("spring.datasource.url") { url }
            registry.add("spring.datasource.username", POSTGRES::getUsername)
            registry.add("spring.datasource.password", POSTGRES::getPassword)
        }
    }

    private companion object {
        // Keep in step with compose.yaml.
        const val POSTGRES_IMAGE = "postgres:18-alpine"

        // Not a container bean: Spring Boot stops a container bean when its context closes, and
        // every context shares this one; Ryuk removes it when the run ends. Each cached context
        // keeps a pool of ten connections open, and up to 32 are cached.
        val POSTGRES: PostgreSQLContainer by lazy {
            PostgreSQLContainer(POSTGRES_IMAGE)
                .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=400")
                .apply { start() }
        }

        private val databases = AtomicInteger()

        fun createDatabase(): String {
            val name = "context_${databases.incrementAndGet()}"
            DriverManager.getConnection(POSTGRES.jdbcUrl, POSTGRES.username, POSTGRES.password).use { connection ->
                connection.createStatement().use { it.execute("create database $name") }
            }
            val port = POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            return "jdbc:postgresql://${POSTGRES.host}:$port/$name"
        }
    }
}
