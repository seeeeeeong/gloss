package gloss.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RulesTest {
    private fun fixtures(pkg: String) = ClassFileImporter().importPackages(pkg)

    // "Method <fixtures.framework.LeakyPolicy.close()> calls ..." names the offender first.
    private val offender = Regex("^\\w+ <fixtures\\.framework\\.(\\w+)")

    @Test
    fun `the purity rule catches a rule or value class reaching past the JDK, directly or not, and only those`() {
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
                "LeakyValue",
            )
    }

    // "Field <fixtures.layers.domain.shop.domain.Invoice.shopService> has type ..." names the offender first.
    private val layerOffender = Regex("^\\w+ <fixtures\\.layers\\.domain\\.shop\\.([a-z.]+\\.[A-Z]\\w*)")

    private fun offendersOf(report: List<String>) =
        report
            .mapNotNull {
                layerOffender.find(it)?.groupValues?.get(1)
            }.toSet()

    @Test
    fun `the layer rule catches each reach outward or past a layer, and only those`() {
        val result = Rules.layers("fixtures.layers").evaluate(fixtures("fixtures.layers"))

        assertThat(offendersOf(result.failureReport.details))
            .containsExactlyInAnyOrder(
                "domain.Invoice",
                "service.Report",
                "presentation.OrderView",
                "dto.request.RefundRequest",
            )
    }

    @Test
    fun `the domain dependency rule catches the model holding SQL, a session or a use case, and only those`() {
        val result = Rules.domainFrameworks("fixtures.layers").evaluate(fixtures("fixtures.layers"))

        assertThat(offendersOf(result.failureReport.details))
            .containsExactlyInAnyOrder("domain.Ledger", "domain.Basket", "domain.Invoice")
    }
}
