package gloss.architecture

import gloss.GlossApplication
import org.assertj.core.api.Assertions.assertThat
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
    fun `every package under gloss is a declared module`() {
        assertThat(Modularity.undeclaredModulePackages(GlossApplication::class.java, modules)).isEmpty()
    }

    @Test
    fun `the modules are the ones declared`() {
        assertThat(
            modules.map { it.identifier.toString() },
        ).containsExactlyInAnyOrder(
            "account",
            "apikey",
            "audit",
            "book",
            "budget",
            "common",
            "generation",
            "llm",
            "post",
        )
    }

    @Test
    fun `the module diagrams are written for reading`() {
        Documenter(modules).writeDocumentation()
    }
}
