package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Each rule, against fixtures that break it, so a passing check means something. */
class RulesTest {
    private fun fixtures(pkg: String) = ClassFileImporter().importPackages(pkg)

    @Test
    fun `the framework rule catches a JDBC dependency`() {
        val result = Rules.frameworkFree().evaluate(fixtures("fixtures.framework"))

        assertThat(result.hasViolation()).isTrue()
        assertThat(result.failureReport.details).anyMatch { it.contains("java.sql.Connection") }
    }
}
