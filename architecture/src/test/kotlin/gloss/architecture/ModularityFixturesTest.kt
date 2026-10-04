package gloss.architecture

import com.tngtech.archunit.core.importer.ImportOption
import fixtures.modulith.cycle.CycleApplication
import fixtures.modulith.hidden.HiddenApplication
import fixtures.modulith.kept.KeptApplication
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
}
