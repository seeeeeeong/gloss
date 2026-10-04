package gloss.architecture

import gloss.GlossApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Each package under `gloss` is a module. A module reaches another only through its
 * top-level package, only if it declares the dependency, and never in a cycle. The
 * fixtures in ModularityFixturesTest show a violation fails.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(GlossApplication::class.java)

    @Test
    fun `modules keep to their declared boundaries`() {
        modules.verify()
    }

    @Test
    fun `the module diagrams are written for reading`() {
        // build/spring-modulith-docs: one diagram per module and the whole picture.
        Documenter(modules).writeDocumentation()
    }
}
