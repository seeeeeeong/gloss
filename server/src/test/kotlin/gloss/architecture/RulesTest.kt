package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RulesTest {
    private fun fixtures(pkg: String) = ClassFileImporter().importPackages(pkg)

    // "Method <fixtures.framework.LeakyPolicy.close()> calls ..." names the offender first.
    private val offender = Regex("^\\w+ <fixtures\\.framework\\.(\\w+)")

    @Test
    fun `the purity rule catches a rule class reaching past the JDK, directly or not, and only those`() {
        val result = Rules.pure().evaluate(fixtures("fixtures.framework"))

        val offenders =
            result.failureReport.details
                .mapNotNull { offender.find(it)?.groupValues?.get(1) }
                .toSet()
        assertThat(offenders)
            .containsExactlyInAnyOrder(
                "LeakyPolicy",
                "FlywayPolicy",
                "IndirectPolicy",
                "ForeignDecisionPolicy",
                "LoggingPolicy",
                "EnvironmentPolicy",
                "ConsolePolicy",
                "Container",
            )
    }
}
