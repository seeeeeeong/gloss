package fixtures.framework

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/** Breaks the rule through a database library no list of frameworks would name. */
class FlywayPolicy(
    private val flyway: Flyway,
) {
    fun migrate(): MigrateResult = flyway.migrate()
}
