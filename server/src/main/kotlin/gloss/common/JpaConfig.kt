package gloss.common

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.SharedEntityManagerCreator

/**
 * Spring Data JPA 4 no longer registers an `EntityManager` bean, and Kotlin JDSL's
 * repository extension needs one. Its documentation for Spring Boot 4 asks for this
 * shared, transaction-bound instance rather than a new one per injection point.
 */
@Configuration
class JpaConfig {
    @Bean
    fun entityManager(factory: EntityManagerFactory): EntityManager =
        SharedEntityManagerCreator.createSharedEntityManager(factory)
}
