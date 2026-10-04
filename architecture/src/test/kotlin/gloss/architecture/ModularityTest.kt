package gloss.architecture

import gloss.GlossApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    private val modules = ApplicationModules.of(GlossApplication::class.java)

    @Test
    fun `modules keep to their declared boundaries`() {
        modules.verify()
    }

    @Test
    fun `the module diagrams are written for reading`() {
        Documenter(modules).writeDocumentation()
    }
}
