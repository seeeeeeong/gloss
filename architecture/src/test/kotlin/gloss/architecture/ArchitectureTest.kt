package gloss.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test

/**
 * The rules applied to the real modules. The fixtures in RulesTest show each
 * rule would fail on a violation. Module boundaries are ModularityTest's.
 */
class ArchitectureTest {
    private fun classesOf(vararg modules: String): JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption { location -> modules.any { location.contains("/$it/build/") } }
            .importClasspath()

    @Test
    fun `core depends on no framework`() {
        Rules.frameworkFree().check(classesOf("core"))
    }
}
