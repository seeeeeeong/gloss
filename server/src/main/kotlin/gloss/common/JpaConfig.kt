package gloss.common

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.SharedEntityManagerCreator

/** Spring Data JPA 4 stopped registering an `EntityManager` bean, and Kotlin JDSL needs one. */
@Configuration
class JpaConfig {
    @Bean
    fun entityManager(factory: EntityManagerFactory): EntityManager =
        SharedEntityManagerCreator.createSharedEntityManager(factory)
}
