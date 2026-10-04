package gloss

import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Clock

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class GlossApplicationTest(
    @Autowired private val clock: Clock,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val jpql: JpqlRenderContext,
) {
    @Test
    fun `the application starts against PostgreSQL with the clock and Kotlin JDSL wired`() {
        assertThat(clock).isNotNull()
        assertThat(entityManager.isOpen).isTrue()
        assertThat(jpql).isNotNull()
    }
}
