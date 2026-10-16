package gloss.architecture

import com.tngtech.archunit.core.importer.ImportOption
import fixtures.modulith.cycle.CycleApplication
import fixtures.modulith.declared.DeclaredApplication
import fixtures.modulith.hidden.HiddenApplication
import fixtures.modulith.kept.KeptApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.core.Violations

class ModularityFixturesTest {
    // Spring Modulith skips test classes unless told otherwise, and the fixtures are test classes.
    private fun modulesOf(application: Class<*>) = ApplicationModules.of(application, ImportOption { true })

    @Test
    fun `two modules that need each other are caught`() {
        assertThatThrownBy { modulesOf(CycleApplication::class.java).verify() }
            .isInstanceOf(Violations::class.java)
            .hasMessageContaining("Cycle")
    }

    @Test
    fun `reaching into another module's sub-package is caught`() {
        assertThatThrownBy { modulesOf(HiddenApplication::class.java).verify() }
            .isInstanceOf(Violations::class.java)
            .hasMessageContaining("non-exposed type")
    }

    @Test
    fun `a module that uses only another's top-level package passes`() {
        assertThatCode { modulesOf(KeptApplication::class.java).verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `a module keeps its layers to itself, except a named interface`() {
        assertThatThrownBy { modulesOf(DeclaredApplication::class.java).verify() }
            .isInstanceOf(Violations::class.java)
            .hasMessageContaining("AEntity")
            .message()
            .doesNotContain("declared.c.C")
    }

    @Test
    fun `a module in the domain container is found`() {
        val modules = modulesOf(DeclaredApplication::class.java)

        assertThat(modules.map { it.identifier.toString() }).contains("f")
    }

    @Test
    fun `a package that declared no module is found, beside the modules or in the domain container`() {
        val modules = modulesOf(DeclaredApplication::class.java)

        assertThat(Modularity.undeclaredModulePackages(DeclaredApplication::class.java, modules, ImportOption { true }))
            .containsExactlyInAnyOrder("fixtures.modulith.declared.d", "fixtures.modulith.declared.domain.e")
    }
}
