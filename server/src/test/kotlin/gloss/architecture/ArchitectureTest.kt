package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val production =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("gloss")

    @Test
    fun `rule classes depend on no framework`() {
        Rules.pure().check(production)
    }

    @Test
    fun `layers depend inward, presentation to application to domain, infrastructure to domain`() {
        Rules.layers("gloss").check(production)
    }

    @Test
    fun `the domain layer holds no web, JDBC or session code`() {
        Rules.domainFrameworks("gloss").check(production)
    }
}
